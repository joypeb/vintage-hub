package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProductListControllerTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private CrawlSiteRepository crawlSiteRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMeasurementRepository measurementRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		measurementRepository.deleteAll();
		productRepository.deleteAll();
		crawlSiteRepository.deleteAll();
	}

	@Test
	void listProductsReturnsLatestProductsWithPageMetadata() throws Exception {
		CrawlSiteEntity site = crawlSiteRepository.save(
			CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60)
		);
		saveProduct(site, "old-shirt", "오래된 셔츠", new BigDecimal("30000"), null, ProductAvailability.AVAILABLE,
			"TOP > SHIRT", Instant.parse("2026-05-14T01:00:00Z"));
		saveProduct(site, "new-pants", "새 팬츠", new BigDecimal("50000"), new BigDecimal("42000"),
			ProductAvailability.SOLD_OUT, "PANTS", Instant.parse("2026-05-15T01:00:00Z"));

		mockMvc.perform(get("/api/products")
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content[0].sourceProductId").value("new-pants"))
			.andExpect(jsonPath("$.data.content[0].displayPrice").value(42000))
			.andExpect(jsonPath("$.data.content[0].siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data.content[0].siteName").value("로켓샐러드"))
			.andExpect(jsonPath("$.data.content[0].stockStatus").value("SOLD_OUT"))
			.andExpect(jsonPath("$.data.content[1].sourceProductId").value("old-shirt"))
			.andExpect(jsonPath("$.data.page").value(0))
			.andExpect(jsonPath("$.data.size").value(10))
			.andExpect(jsonPath("$.data.totalElements").value(2))
			.andExpect(jsonPath("$.data.totalPages").value(1))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void listProductsAppliesSiteCategoryStockPriceAndMeasurementFilters() throws Exception {
		CrawlSiteEntity rocketSalad = crawlSiteRepository.save(
			CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60)
		);
		CrawlSiteEntity otherSite = crawlSiteRepository.save(
			CrawlSiteEntity.create("other", "다른샵", URI.create("https://example.com"), "Custom", 60)
		);
		ProductEntity matched = saveProduct(rocketSalad, "matched-pants", "조건에 맞는 팬츠", new BigDecimal("55000"),
			new BigDecimal("43000"), ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T01:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(matched, "허리", new BigDecimal("42.00"), "허리 42cm"));
		ProductEntity smallWaist = saveProduct(rocketSalad, "small-pants", "허리가 작은 팬츠", new BigDecimal("40000"),
			null, ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T02:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(smallWaist, "허리", new BigDecimal("36.00"), "허리 36cm"));
		saveProduct(rocketSalad, "sold-shirt", "품절 셔츠", new BigDecimal("43000"), null,
			ProductAvailability.SOLD_OUT, "TOP > SHIRT", Instant.parse("2026-05-15T03:00:00Z"));
		saveProduct(otherSite, "other-pants", "다른 사이트 팬츠", new BigDecimal("43000"), null,
			ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T04:00:00Z"));

		mockMvc.perform(get("/api/products")
				.param("siteCode", "rocketsalad")
				.param("standardCategory", "하의")
				.param("standardSubCategory", "팬츠")
				.param("stockStatus", "AVAILABLE")
				.param("minPrice", "40000")
				.param("maxPrice", "45000")
				.param("measurementPart", "허리")
				.param("minMeasurement", "40")
				.param("maxMeasurement", "44")
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].sourceProductId").value("matched-pants"))
			.andExpect(jsonPath("$.data.content[0].standardCategory").value("하의"))
			.andExpect(jsonPath("$.data.content[0].standardSubCategory").value("팬츠"))
			.andExpect(jsonPath("$.data.content[0].displayPrice").value(43000))
			.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@Test
	void listProductsAppliesMultipleMeasurementFilters() throws Exception {
		CrawlSiteEntity site = crawlSiteRepository.save(
			CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60)
		);
		ProductEntity matched = saveProduct(site, "matched-pants", "조건에 맞는 팬츠", new BigDecimal("55000"),
			new BigDecimal("43000"), ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T01:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(matched, "허리", new BigDecimal("45.00"), "허리 45cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(matched, "허벅지", new BigDecimal("32.00"), "허벅지 32cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(matched, "밑단", new BigDecimal("25.00"), "밑단 25cm"));
		ProductEntity smallThigh = saveProduct(site, "small-thigh-pants", "허벅지가 작은 팬츠", new BigDecimal("45000"),
			null, ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T02:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(smallThigh, "허리", new BigDecimal("45.00"), "허리 45cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(smallThigh, "허벅지", new BigDecimal("28.00"), "허벅지 28cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(smallThigh, "밑단", new BigDecimal("25.00"), "밑단 25cm"));
		ProductEntity wideHem = saveProduct(site, "wide-hem-pants", "밑단이 큰 팬츠", new BigDecimal("45000"),
			null, ProductAvailability.AVAILABLE, "PANTS", Instant.parse("2026-05-15T03:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(wideHem, "허리", new BigDecimal("45.00"), "허리 45cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(wideHem, "허벅지", new BigDecimal("32.00"), "허벅지 32cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(wideHem, "밑단", new BigDecimal("35.00"), "밑단 35cm"));

		mockMvc.perform(get("/api/products")
				.param("measurementFilters", "허리:40:50", "허벅지:30", "밑단:20:30")
				.param("page", "0")
				.param("size", "10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.content.length()").value(1))
			.andExpect(jsonPath("$.data.content[0].sourceProductId").value("matched-pants"))
			.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	private ProductEntity saveProduct(CrawlSiteEntity site, String sourceProductId, String name, BigDecimal originalPrice,
			BigDecimal salePrice, ProductAvailability availability, String sourceCategoryName, Instant collectedAt) {
		CrawledProductRef ref = new CrawledProductRef(sourceProductId,
			URI.create("https://example.com/products/" + sourceProductId));
		CrawledProductDetail detail = new CrawledProductDetail(ref, name, originalPrice, salePrice, availability,
			name + " 상세 설명", URI.create("https://example.com/images/" + sourceProductId + ".jpg"),
			sourceCategoryName, Map.of());
		CrawledProductSummary summary = new CrawledProductSummary(ref, name,
			URI.create("https://example.com/images/" + sourceProductId + ".jpg"));
		ProductEntity product = ProductEntity.create(site, sourceProductId);
		product.updateFrom(detail, summary, collectedAt);
		return productRepository.save(product);
	}
}
