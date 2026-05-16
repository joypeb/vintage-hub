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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(RocketSaladCrawler.class);
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
	public List<CrawlCursor> initialCursors() {
		// 로켓샐러드의 주요 카테고리를 각각 최신순 1페이지부터 수집한다.
		return List.of(
			new CrawlCursor("TOP:1"),
			new CrawlCursor("OUTER:1"),
			new CrawlCursor("PANTS:1"),
			new CrawlCursor("ACC:1"),
			new CrawlCursor("WOMEN:1")
		);
	}

	@Override
	public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
		// 커서는 "카테고리:페이지" 형식이며 모바일 목록 URL의 xcode/page로 변환된다.
		ListCursor listCursor = ListCursor.parse(cursor);
		URI listUrl = mobileListUrl(site.baseUrl(), listCursor);
		log.atDebug()
			.addKeyValue("event", "crawl.rocketsalad.list.fetch.started")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("cursor", listCursor)
			.addKeyValue("url", listUrl)
			.log("crawl.rocketsalad.list.fetch.started");
		Document document = Jsoup.parse(pageClient.get(listUrl), listUrl.toString());
		// 상품 링크를 기준으로 카드 정보를 추출하고 같은 branduid가 중복되면 제거한다.
		List<CrawledProductSummary> products = document.select("a[href*=/m/product.html?branduid=]").stream()
			.map(anchor -> toSummary(site.baseUrl(), anchor))
			.flatMap(Optional::stream)
			.distinct()
			.toList();
		log.atDebug()
			.addKeyValue("event", "crawl.rocketsalad.list.parsed")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("cursor", listCursor)
			.addKeyValue("url", listUrl)
			.addKeyValue("productCount", products.size())
			.log("crawl.rocketsalad.list.parsed");

		// 목록 HTML에는 마지막 페이지 신호가 없으므로 다음 페이지 커서를 항상 넘기고 상위 서비스가 중단한다.
		return new CrawlListResult(products, new CrawlCursor(listCursor.category() + ":" + (listCursor.page() + 1)));
	}

	@Override
	public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
		// 상세 페이지는 모바일 URL이 구조화 데이터와 DOM 파싱에 필요한 정보를 더 안정적으로 제공한다.
		URI mobileDetailUrl = mobileDetailUrl(site.baseUrl(), productRef.sourceProductId());
		log.atDebug()
			.addKeyValue("event", "crawl.rocketsalad.detail.fetch.started")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("sourceProductId", productRef.sourceProductId())
			.addKeyValue("url", mobileDetailUrl)
			.log("crawl.rocketsalad.detail.fetch.started");
		Document document = Jsoup.parse(pageClient.get(mobileDetailUrl), mobileDetailUrl.toString());
		Optional<JsonNode> productJson = productJson(document);
		JsonNode offers = productJson.map(json -> json.path("offers")).orElse(null);
		// 실측값은 상세 이미지 영역의 텍스트에 섞여 있으므로 공백을 정리한 뒤 정규식으로 읽는다.
		String detailText = normalize(document.selectFirst("#detail_img1") == null ? "" : document.selectFirst("#detail_img1").text());

		// JSON-LD를 우선 사용하되 누락 필드는 기존 DOM 셀렉터로 보완한다.
		CrawledProductDetail detail = new CrawledProductDetail(
			productRef,
			firstText(productJson.map(json -> json.path("name").asText(null)).orElse(null),
				document.select("#detail-item h2").text(), document.title()),
			firstPrice(offers == null ? null : offers.path("price").asText(null), inputValue(document, "#price"),
				document.select("#pricevalue").text()),
			price(inputValue(document, "#disprice")),
			availability(productJson, document),
			detailText,
			firstImage(site.baseUrl(), productJson, document),
			productJson.map(json -> json.path("category").asText(null)).orElse(null),
			measurements(detailText)
		);
		log.atDebug()
			.addKeyValue("event", "crawl.rocketsalad.detail.parsed")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("sourceProductId", productRef.sourceProductId())
			.addKeyValue("namePresent", detail.name() != null && !detail.name().isBlank())
			.addKeyValue("price", detail.originalPrice())
			.addKeyValue("stockStatus", detail.availability())
			.addKeyValue("imagePresent", detail.thumbnailImageUrl() != null)
			.addKeyValue("descriptionLength", detailText == null ? 0 : detailText.length())
			.addKeyValue("measurementCount", detail.measurements() == null ? 0 : detail.measurements().size())
			.log("crawl.rocketsalad.detail.parsed");
		return detail;
	}

	@Override
	public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
		return fetchDetail(site, productRef).availability();
	}

	private Optional<CrawledProductSummary> toSummary(URI baseUrl, Element anchor) {
		String href = anchor.attr("abs:href");
		String sourceProductId = sourceProductId(href).orElse(null);
		if (sourceProductId == null) {
			// branduid가 없는 링크는 상품 식별자로 저장할 수 없어 건너뛴다.
			return Optional.empty();
		}
		Element productCard = anchor.closest("li");
		Element productScope = productCard == null ? anchor : productCard;
		// 카드 영역과 앵커 내부를 모두 확인해 테마별 마크업 차이를 흡수한다.
		String name = firstText(productScope.select(".pname").text(), anchor.select(".pname").text(), anchor.attr("title"));
		URI imageUrl = absoluteUrl(baseUrl, firstText(
			productScope.select(".MS_prod_mobile_image").attr("src"),
			productScope.select(".thumb-img img").attr("src"),
			anchor.select(".MS_prod_mobile_image").attr("src")
		));
		CrawledProductRef ref = new CrawledProductRef(sourceProductId, pcDetailUrl(baseUrl, sourceProductId));
		return Optional.of(new CrawledProductSummary(ref, name, imageUrl));
	}

	private Optional<JsonNode> productJson(Document document) {
		// JSON-LD 스크립트가 여러 개일 수 있어 Product 타입이 나올 때까지 순회한다.
		for (Element script : document.select("script[type=application/ld+json]")) {
			Optional<JsonNode> product = parseProductJson(script.data().isBlank() ? script.html() : script.data());
			if (product.isPresent()) {
				return product;
			}
		}
		return Optional.empty();
	}

	private Optional<JsonNode> parseProductJson(String json) {
		try {
			return findProductJson(objectMapper.readTree(json));
		}
		catch (Exception exception) {
			// 일부 페이지는 JSON-LD 안의 작은따옴표를 이스케이프해 표준 JSON 파서가 실패한다.
			log.atDebug()
				.addKeyValue("event", "crawl.rocketsalad.jsonld.parse.retry")
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.rocketsalad.jsonld.parse.retry");
			try {
				return findProductJson(objectMapper.readTree(normalizeJsonLd(json)));
			}
			catch (Exception retryException) {
				// 구조화 데이터가 깨져도 DOM 파싱으로 계속 진행할 수 있게 빈 값으로 돌려준다.
				log.atWarn()
					.addKeyValue("event", "crawl.rocketsalad.jsonld.parse.failed")
					.addKeyValue("fallback", "dom")
					.addKeyValue("reason", retryException.getMessage())
					.log("crawl.rocketsalad.jsonld.parse.failed");
				return Optional.empty();
			}
		}
	}

	private String normalizeJsonLd(String json) {
		return json.replace("\\'", "'");
	}

	private Optional<JsonNode> findProductJson(JsonNode node) {
		if (node == null || node.isMissingNode()) {
			return Optional.empty();
		}
		if (node.isArray()) {
			// 배열형 JSON-LD에서는 자식 노드 중 Product 타입을 재귀적으로 찾는다.
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
			// @graph 안에 실제 Product 노드가 들어가는 JSON-LD 구조를 지원한다.
			return findProductJson(graph);
		}
		return Optional.empty();
	}

	private ProductAvailability availability(Optional<JsonNode> productJson, Document document) {
		// 구조화 데이터의 schema.org availability가 가장 신뢰도가 높다.
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
			// JSON-LD가 없거나 부정확한 경우 화면의 품절 표시를 보조 신호로 사용한다.
			return ProductAvailability.SOLD_OUT;
		}
		return ProductAvailability.UNKNOWN;
	}

	private URI firstImage(URI baseUrl, Optional<JsonNode> productJson, Document document) {
		// 대표 이미지는 JSON-LD, 상세 DOM, OG 태그 순서로 신뢰도를 둔다.
		String jsonImage = productJson.map(json -> {
			JsonNode image = json.path("image");
			if (image.isArray() && !image.isEmpty()) {
				return image.get(0).asText(null);
			}
			return image.asText(null);
		}).orElse(null);
		return absoluteUrl(baseUrl, firstText(
			jsonImage,
			document.select("#detail-item .items img").attr("src"),
			document.select(".MS_prod_mobile_image").attr("src"),
			document.select("meta[property=og:image]").attr("content")
		));
	}

	private Map<String, String> measurements(String text) {
		Map<String, String> values = new LinkedHashMap<>();
		Matcher matcher = MEASUREMENT_PATTERN.matcher(text);
		while (matcher.find()) {
			// 같은 부위가 여러 번 나오면 뒤쪽 값을 최신/최종 표기로 본다.
			values.put(matcher.group(1), matcher.group(2));
		}
		return values;
	}

	private URI mobileListUrl(URI baseUrl, ListCursor cursor) {
		// 내부 카테고리 코드를 로켓샐러드 모바일 목록의 xcode 값으로 변환한다.
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
			// 프로토콜 상대 URL은 기준 URL의 scheme을 붙여 절대 URL로 만든다.
			return URI.create(baseUrl.getScheme() + ":" + url);
		}
		return baseUrl.resolve(url);
	}

	private String inputValue(Document document, String selector) {
		Element input = document.selectFirst(selector);
		return input == null ? null : input.attr("value");
	}

	private BigDecimal firstPrice(String... values) {
		// 여러 후보 가격 중 숫자로 파싱되는 첫 값을 대표 가격으로 사용한다.
		for (String value : values) {
			BigDecimal parsedPrice = price(value);
			if (parsedPrice != null) {
				return parsedPrice;
			}
		}
		return null;
	}

	private BigDecimal price(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		// 통화 기호, 쉼표, 원문 텍스트를 제거하고 숫자/소수점만 남긴다.
		String digits = value.replaceAll("[^0-9.]", "");
		if (digits.isBlank()) {
			return null;
		}
		return new BigDecimal(digits);
	}

	private String firstText(String... values) {
		// 후보 문자열을 정규화한 뒤 가장 먼저 비어 있지 않은 값을 선택한다.
		for (String value : values) {
			String normalizedValue = normalize(value);
			if (normalizedValue != null && !normalizedValue.isBlank()) {
				return normalizedValue;
			}
		}
		return null;
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("\\s+", " ").trim();
	}

	private record ListCursor(String category, int page) {

		private static ListCursor parse(CrawlCursor cursor) {
			// 잘못된 커서가 들어오면 기본 카테고리 첫 페이지로 복구해 크롤링을 계속한다.
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
