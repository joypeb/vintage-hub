package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlRunServiceTest {

	@Test
	void requestManualRunUpsertsProductsAndMeasurements() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		SiteCrawler crawler = new StubSiteCrawler();
		CrawlRunService service = new CrawlRunService(
			siteRepository,
			runRepository,
			productRepository,
			measurementRepository,
			new CrawlerRegistry(List.of(crawler))
		);
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		when(siteRepository.findByCode("rocketsalad")).thenReturn(Optional.of(site));
		when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(productRepository.findBySiteAndSourceProductId(site, "521529")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(result.siteCode()).isEqualTo("rocketsalad");
		assertThat(result.status()).isEqualTo("SUCCEEDED");
		assertThat(result.foundCount()).isEqualTo(1);
		assertThat(result.createdCount()).isEqualTo(1);
		assertThat(result.updatedCount()).isZero();
		assertThat(result.failedCount()).isZero();
		assertThat(site.lastCrawledAt()).isNotNull();
		assertThat(site.lastChangedAt()).isNotNull();
		assertThat(site.lastChangeDetectedAt()).isNotNull();
		ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
		org.mockito.Mockito.verify(productRepository).save(productCaptor.capture());
		ProductEntity savedProduct = productCaptor.getValue();
		assertThat(savedProduct.name()).isEqualTo("90`s Levi`s 550 Relaxed Fit Denim Shorts (33)");
		assertThat(savedProduct.stockStatus()).isEqualTo(ProductAvailability.AVAILABLE);
		assertThat(savedProduct.originalPrice()).isEqualByComparingTo("55000");
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<List<ProductMeasurementEntity>> measurementCaptor = ArgumentCaptor.forClass((Class) List.class);
		org.mockito.Mockito.verify(measurementRepository).saveAll(measurementCaptor.capture());
		assertThat(measurementCaptor.getValue()).extracting(ProductMeasurementEntity::part)
			.containsExactlyInAnyOrder("허리", "기장");
	}

	private static class StubSiteCrawler implements SiteCrawler {

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, com.joypeb.vintagehub.crawl.domain.CrawlCursor cursor) {
			CrawledProductRef ref = new CrawledProductRef("521529",
				URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521529"));
			return new CrawlListResult(List.of(new CrawledProductSummary(ref, "fallback",
				URI.create("https://www.rocketsalad.co.kr/shopimages/item.jpg"))), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			return new CrawledProductDetail(
				productRef,
				"90`s Levi`s 550 Relaxed Fit Denim Shorts (33)",
				new BigDecimal("55000"),
				null,
				ProductAvailability.AVAILABLE,
				"Size: 허리 44cm 기장 51cm",
				URI.create("https://www.rocketsalad.co.kr/shopimages/item.jpg"),
				"Pants > casual",
				Map.of("허리", "44", "기장", "51")
			);
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.AVAILABLE;
		}
	}
}
