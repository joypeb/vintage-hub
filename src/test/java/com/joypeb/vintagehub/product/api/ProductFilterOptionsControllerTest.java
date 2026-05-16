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
class ProductFilterOptionsControllerTest {

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
	void getFilterOptionsReturnsOnlyValuesPresentInProducts() throws Exception {
		CrawlSiteEntity rocketSalad = crawlSiteRepository.save(
			CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60)
		);
		CrawlSiteEntity otherSite = crawlSiteRepository.save(
			CrawlSiteEntity.create("other", "다른샵", URI.create("https://example.com"), "Custom", 60)
		);
		crawlSiteRepository.save(
			CrawlSiteEntity.create("empty", "빈샵", URI.create("https://empty.example.com"), "Custom", 60)
		);
		ProductEntity rocketPants = saveProduct(rocketSalad, "rocket-pants", "로켓 팬츠", "PANTS",
			Instant.parse("2026-05-15T01:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(rocketPants, "허리", new BigDecimal("42.00"), "허리 42cm"));
		measurementRepository.save(ProductMeasurementEntity.automatic(rocketPants, "밑단", new BigDecimal("24.00"), "밑단 24cm"));
		saveProduct(rocketSalad, "rocket-pants-2", "로켓 팬츠 2", "PANTS",
			Instant.parse("2026-05-15T01:30:00Z"));
		ProductEntity rocketShirt = saveProduct(rocketSalad, "rocket-shirt", "로켓 셔츠", "TOP > SHIRT",
			Instant.parse("2026-05-15T02:00:00Z"));
		measurementRepository.save(ProductMeasurementEntity.automatic(rocketShirt, "가슴", new BigDecimal("56.00"), "가슴 56cm"));
		saveProduct(otherSite, "other-pants", "다른샵 팬츠", "PANTS",
			Instant.parse("2026-05-15T03:00:00Z"));

		mockMvc.perform(get("/api/products/filter-options"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.sites.length()").value(2))
			.andExpect(jsonPath("$.data.sites[0].code").value("rocketsalad"))
			.andExpect(jsonPath("$.data.sites[0].name").value("로켓샐러드"))
			.andExpect(jsonPath("$.data.sites[0].productCount").value(3))
			.andExpect(jsonPath("$.data.sites[1].code").value("other"))
			.andExpect(jsonPath("$.data.sites[1].productCount").value(1))
			.andExpect(jsonPath("$.data.categories.length()").value(2))
			.andExpect(jsonPath("$.data.categories[0].name").value("하의"))
			.andExpect(jsonPath("$.data.categories[0].productCount").value(2))
			.andExpect(jsonPath("$.data.categories[0].subCategories[0].name").value("팬츠"))
			.andExpect(jsonPath("$.data.categories[0].subCategories[0].productCount").value(2))
			.andExpect(jsonPath("$.data.categories[1].name").value("상의"))
			.andExpect(jsonPath("$.data.categories[1].productCount").value(1))
			.andExpect(jsonPath("$.data.categories[1].subCategories[0].name").value("셔츠"))
			.andExpect(jsonPath("$.data.measurements.length()").value(3))
			.andExpect(jsonPath("$.data.measurements[0].part").value("가슴"))
			.andExpect(jsonPath("$.data.measurements[0].productCount").value(1))
			.andExpect(jsonPath("$.data.measurements[1].part").value("밑단"))
			.andExpect(jsonPath("$.data.measurements[1].productCount").value(1))
			.andExpect(jsonPath("$.data.measurements[2].part").value("허리"))
			.andExpect(jsonPath("$.data.measurements[2].productCount").value(1))
			.andExpect(jsonPath("$.data.sorts.length()").value(3))
			.andExpect(jsonPath("$.data.sorts[0].code").value("LATEST"))
			.andExpect(jsonPath("$.data.sorts[0].name").value("최신순"))
			.andExpect(jsonPath("$.data.sorts[1].code").value("PRICE_LOW"))
			.andExpect(jsonPath("$.data.sorts[1].name").value("가격 낮은순"))
			.andExpect(jsonPath("$.data.sorts[2].code").value("PRICE_HIGH"))
			.andExpect(jsonPath("$.data.sorts[2].name").value("가격 높은순"))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	private ProductEntity saveProduct(CrawlSiteEntity site, String sourceProductId, String name,
			String sourceCategoryName, Instant collectedAt) {
		CrawledProductRef ref = new CrawledProductRef(sourceProductId,
			URI.create("https://example.com/products/" + sourceProductId));
		CrawledProductDetail detail = new CrawledProductDetail(ref, name, new BigDecimal("50000"), null,
			ProductAvailability.AVAILABLE, name + " 상세 설명",
			URI.create("https://example.com/images/" + sourceProductId + ".jpg"), sourceCategoryName, Map.of());
		CrawledProductSummary summary = new CrawledProductSummary(ref, name,
			URI.create("https://example.com/images/" + sourceProductId + ".jpg"));
		ProductEntity product = ProductEntity.create(site, sourceProductId);
		product.updateFrom(detail, summary, collectedAt);
		return productRepository.save(product);
	}
}
