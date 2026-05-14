package com.joypeb.vintagehub.crawl.site.rocketsalad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RocketSaladCrawler implements SiteCrawler {

	private static final String SITE_CODE = "rocketsalad";
	private static final String DEFAULT_CURSOR = "PANTS:1";
	private static final Pattern BRANDUID_PATTERN = Pattern.compile("[?&]branduid=([^&]+)");
	private static final Pattern MEASUREMENT_PATTERN = Pattern.compile("(가슴|어깨|팔길이|소매|기장|총장|허리|허벅지|밑위|밑단)\\s*(\\d+(?:\\.\\d+)?)\\s*cm");
	private static final Map<String, String> CATEGORY_XCODES = Map.of(
		"TOP", "113",
		"OUTER", "118",
		"PANTS", "115",
		"ACC", "116",
		"WOMEN", "094"
	);

	private final RocketSaladPageClient pageClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public RocketSaladCrawler(RocketSaladPageClient pageClient) {
		this(pageClient, new ObjectMapper());
	}

	RocketSaladCrawler(RocketSaladPageClient pageClient, ObjectMapper objectMapper) {
		this.pageClient = pageClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public String siteCode() {
		return SITE_CODE;
	}

	@Override
	public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
		ListCursor listCursor = ListCursor.parse(cursor);
		URI listUrl = mobileListUrl(site.baseUrl(), listCursor);
		Document document = Jsoup.parse(pageClient.get(listUrl), listUrl.toString());
		List<CrawledProductSummary> products = document.select("a[href*=/m/product.html?branduid=]").stream()
			.map(anchor -> toSummary(site.baseUrl(), anchor))
			.flatMap(Optional::stream)
			.distinct()
			.toList();

		return new CrawlListResult(products, new CrawlCursor(listCursor.category() + ":" + (listCursor.page() + 1)));
	}

	@Override
	public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
		URI mobileDetailUrl = mobileDetailUrl(site.baseUrl(), productRef.sourceProductId());
		Document document = Jsoup.parse(pageClient.get(mobileDetailUrl), mobileDetailUrl.toString());
		Optional<JsonNode> productJson = productJson(document);
		JsonNode offers = productJson.map(json -> json.path("offers")).orElse(null);
		String detailText = normalize(document.selectFirst("#detail_img1") == null ? "" : document.selectFirst("#detail_img1").text());

		return new CrawledProductDetail(
			productRef,
			firstText(productJson.map(json -> json.path("name").asText(null)).orElse(null), document.title()),
			firstPrice(offers == null ? null : offers.path("price").asText(null), inputValue(document, "#price")),
			price(inputValue(document, "#disprice")),
			availability(productJson, document),
			detailText,
			firstImage(site.baseUrl(), productJson, document),
			productJson.map(json -> json.path("category").asText(null)).orElse(null),
			measurements(detailText)
		);
	}

	@Override
	public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
		return fetchDetail(site, productRef).availability();
	}

	private Optional<CrawledProductSummary> toSummary(URI baseUrl, Element anchor) {
		String href = anchor.attr("abs:href");
		String sourceProductId = sourceProductId(href).orElse(null);
		if (sourceProductId == null) {
			return Optional.empty();
		}
		String name = firstText(anchor.select(".pname").text(), anchor.attr("title"));
		URI imageUrl = absoluteUrl(baseUrl, anchor.select(".MS_prod_mobile_image").attr("src"));
		CrawledProductRef ref = new CrawledProductRef(sourceProductId, pcDetailUrl(baseUrl, sourceProductId));
		return Optional.of(new CrawledProductSummary(ref, name, imageUrl));
	}

	private Optional<JsonNode> productJson(Document document) {
		for (Element script : document.select("script[type=application/ld+json]")) {
			try {
				JsonNode root = objectMapper.readTree(script.data().isBlank() ? script.html() : script.data());
				Optional<JsonNode> product = findProductJson(root);
				if (product.isPresent()) {
					return product;
				}
			}
			catch (Exception ignored) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private Optional<JsonNode> findProductJson(JsonNode node) {
		if (node == null || node.isMissingNode()) {
			return Optional.empty();
		}
		if (node.isArray()) {
			for (JsonNode child : node) {
				Optional<JsonNode> product = findProductJson(child);
				if (product.isPresent()) {
					return product;
				}
			}
		}
		if (node.isObject() && "Product".equals(node.path("@type").asText())) {
			return Optional.of(node);
		}
		JsonNode graph = node.path("@graph");
		if (graph.isArray()) {
			return findProductJson(graph);
		}
		return Optional.empty();
	}

	private ProductAvailability availability(Optional<JsonNode> productJson, Document document) {
		String availability = productJson
			.map(json -> json.path("offers").path("availability").asText(""))
			.orElse("");
		if (availability.endsWith("/InStock")) {
			return ProductAvailability.AVAILABLE;
		}
		if (availability.endsWith("/OutOfStock")) {
			return ProductAvailability.SOLD_OUT;
		}
		if (!document.select(".is_soldout, .sold-out").isEmpty() || document.text().contains("SOLD OUT")) {
			return ProductAvailability.SOLD_OUT;
		}
		return ProductAvailability.UNKNOWN;
	}

	private URI firstImage(URI baseUrl, Optional<JsonNode> productJson, Document document) {
		String jsonImage = productJson.map(json -> {
			JsonNode image = json.path("image");
			if (image.isArray() && !image.isEmpty()) {
				return image.get(0).asText(null);
			}
			return image.asText(null);
		}).orElse(null);
		return absoluteUrl(baseUrl, firstText(jsonImage, document.select(".MS_prod_mobile_image").attr("src")));
	}

	private Map<String, String> measurements(String text) {
		Map<String, String> values = new LinkedHashMap<>();
		Matcher matcher = MEASUREMENT_PATTERN.matcher(text);
		while (matcher.find()) {
			values.put(matcher.group(1), matcher.group(2));
		}
		return values;
	}

	private URI mobileListUrl(URI baseUrl, ListCursor cursor) {
		String xcode = CATEGORY_XCODES.getOrDefault(cursor.category(), CATEGORY_XCODES.get("PANTS"));
		return baseUrl.resolve("/m/product_list.html?xcode=" + xcode + "&type=X&viewtype=gallery&page=" + cursor.page() + "&sort=order");
	}

	private URI mobileDetailUrl(URI baseUrl, String sourceProductId) {
		return baseUrl.resolve("/m/product.html?branduid=" + sourceProductId);
	}

	private URI pcDetailUrl(URI baseUrl, String sourceProductId) {
		return baseUrl.resolve("/shop/shopdetail.html?branduid=" + sourceProductId);
	}

	private Optional<String> sourceProductId(String url) {
		Matcher matcher = BRANDUID_PATTERN.matcher(url);
		if (matcher.find()) {
			return Optional.of(matcher.group(1));
		}
		return Optional.empty();
	}

	private URI absoluteUrl(URI baseUrl, String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		if (url.startsWith("//")) {
			return URI.create(baseUrl.getScheme() + ":" + url);
		}
		return baseUrl.resolve(url);
	}

	private String inputValue(Document document, String selector) {
		Element input = document.selectFirst(selector);
		return input == null ? null : input.attr("value");
	}

	private BigDecimal firstPrice(String first, String second) {
		BigDecimal firstPrice = price(first);
		return firstPrice == null ? price(second) : firstPrice;
	}

	private BigDecimal price(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String digits = value.replaceAll("[^0-9.]", "");
		if (digits.isBlank()) {
			return null;
		}
		return new BigDecimal(digits);
	}

	private String firstText(String first, String second) {
		String normalizedFirst = normalize(first);
		if (normalizedFirst != null && !normalizedFirst.isBlank()) {
			return normalizedFirst;
		}
		return normalize(second);
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("\\s+", " ").trim();
	}

	private record ListCursor(String category, int page) {

		private static ListCursor parse(CrawlCursor cursor) {
			String value = cursor == null || cursor.value() == null || cursor.value().isBlank() ? DEFAULT_CURSOR : cursor.value();
			String[] parts = value.split(":", 2);
			if (parts.length != 2) {
				return new ListCursor("PANTS", 1);
			}
			try {
				return new ListCursor(parts[0], Integer.parseInt(parts[1]));
			}
			catch (NumberFormatException exception) {
				return new ListCursor(parts[0], 1);
			}
		}
	}
}
