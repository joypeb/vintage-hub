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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ProductDetailControllerTest {

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
	void getProductDetailReturnsDescriptionOriginalUrlAndMeasurements() throws Exception {
		CrawlSiteEntity site = crawlSiteRepository.save(
			CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60)
		);
		ProductEntity product = saveProduct(site, "521529", "90s Levi's denim shorts", new BigDecimal("55000"),
			null, ProductAvailability.AVAILABLE, "Pants > casual", Instant.parse("2026-05-15T01:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(product, "허리", new BigDecimal("44.00"), "허리 44cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(product, "허벅지", new BigDecimal("36.00"), "허벅지 36cm"));

		mockMvc.perform(get("/api/products/{siteCode}/{sourceProductId}", "rocketsalad", "521529"))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "max-age=60, must-revalidate"))
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.id").value(product.id()))
			.andExpect(jsonPath("$.data.sourceProductId").value("521529"))
			.andExpect(jsonPath("$.data.name").value("90s Levi's denim shorts"))
			.andExpect(jsonPath("$.data.originalPrice").value(55000))
			.andExpect(jsonPath("$.data.displayPrice").value(55000))
			.andExpect(jsonPath("$.data.description").value("90s Levi's denim shorts 상세 설명"))
			.andExpect(jsonPath("$.data.detailUrl").value("https://example.com/products/521529"))
			.andExpect(jsonPath("$.data.thumbnailImageUrl").value("https://example.com/images/521529.jpg"))
			.andExpect(jsonPath("$.data.siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data.siteName").value("로켓샐러드"))
			.andExpect(jsonPath("$.data.standardCategory").value("하의"))
			.andExpect(jsonPath("$.data.standardSubCategory").value("팬츠"))
			.andExpect(jsonPath("$.data.categoryConfidence").doesNotExist())
			.andExpect(jsonPath("$.data.stockStatus").value("AVAILABLE"))
			.andExpect(jsonPath("$.data.measurements.length()").value(2))
			.andExpect(jsonPath("$.data.measurements[0].part").value("허리"))
			.andExpect(jsonPath("$.data.measurements[0].valueCm").value(44.00))
			.andExpect(jsonPath("$.data.measurements[0].rawText").doesNotExist())
			.andExpect(jsonPath("$.data.measurements[0].confidence").doesNotExist())
			.andExpect(jsonPath("$.data.measurements[0].source").doesNotExist())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void getProductDetailReturnsNotFoundForUnknownProduct() throws Exception {
		mockMvc.perform(get("/api/products/{siteCode}/{sourceProductId}", "rocketsalad", "unknown"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_002"));
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
