package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
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
		verify(productRepository).save(productCaptor.capture());
		ProductEntity savedProduct = productCaptor.getValue();
		assertThat(savedProduct.name()).isEqualTo("90`s Levi`s 550 Relaxed Fit Denim Shorts (33)");
		assertThat(savedProduct.stockStatus()).isEqualTo(ProductAvailability.AVAILABLE);
		assertThat(savedProduct.originalPrice()).isEqualByComparingTo("55000");
		@SuppressWarnings({"unchecked", "rawtypes"})
		ArgumentCaptor<List<ProductMeasurementEntity>> measurementCaptor = ArgumentCaptor.forClass((Class) List.class);
		verify(measurementRepository).saveAll(measurementCaptor.capture());
		assertThat(measurementCaptor.getValue()).extracting(ProductMeasurementEntity::part)
			.containsExactlyInAnyOrder("허리", "기장");
	}

	@Test
	void requestManualRunStoresRocketSaladStandardCategories() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		RocketSaladCategoryCrawler crawler = new RocketSaladCategoryCrawler();
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
		when(productRepository.findBySiteAndSourceProductId(any(), any())).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.requestManualRun("rocketsalad");

		ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
		verify(productRepository, times(4)).save(productCaptor.capture());
		assertThat(productCaptor.getAllValues())
			.extracting("sourceProductId", "standardCategory", "standardSubCategory", "categoryConfidence")
			.containsExactly(
				tuple("shirt", "상의", "셔츠", new BigDecimal("0.950")),
				tuple("pants", "하의", "팬츠", new BigDecimal("0.950")),
				tuple("outer", "아우터", null, new BigDecimal("0.800")),
				tuple("women", "아우터", "자켓", new BigDecimal("0.800"))
			);
	}

	@Test
	void requestManualRunWalksInitialCursorsAndStopsPagingWhenExistingProductAppears() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		PagedSiteCrawler crawler = new PagedSiteCrawler();
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
		when(productRepository.findBySiteAndSourceProductId(site, "top-new")).thenReturn(Optional.empty());
		when(productRepository.findBySiteAndSourceProductId(site, "top-old"))
			.thenReturn(Optional.of(completeProduct(site, "top-old")));
		when(productRepository.findBySiteAndSourceProductId(site, "pants-new")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(result.foundCount()).isEqualTo(3);
		assertThat(result.createdCount()).isEqualTo(2);
		assertThat(result.updatedCount()).isZero();
		assertThat(result.failedCount()).isZero();
		assertThat(crawler.fetchedCursors).containsExactly("TOP:1", "TOP:2", "PANTS:1");
		assertThat(crawler.fetchedDetails).containsExactly("top-new", "pants-new");
		ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
		verify(productRepository, times(2)).save(productCaptor.capture());
		assertThat(productCaptor.getAllValues()).extracting(ProductEntity::stockStatus)
			.containsOnly(ProductAvailability.SOLD_OUT);
	}

	@Test
	void requestManualRunRecordsProductFailureReasonsInRunMessage() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		FailingDetailCrawler crawler = new FailingDetailCrawler();
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
		when(productRepository.findBySiteAndSourceProductId(site, "fail-1")).thenReturn(Optional.empty());

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(result.status()).isEqualTo("SUCCEEDED");
		assertThat(result.foundCount()).isEqualTo(1);
		assertThat(result.createdCount()).isZero();
		assertThat(result.updatedCount()).isZero();
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.message()).contains("fail-1");
		assertThat(result.message()).contains("detail exploded");
	}

	@Test
	void requestManualRunLimitsPagingPerInitialCursor() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		NeverEndingSiteCrawler crawler = new NeverEndingSiteCrawler();
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
		when(productRepository.findBySiteAndSourceProductId(any(), any())).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(crawler.fetchedCursors).containsExactly("TOP:1", "TOP:2", "TOP:3");
		assertThat(result.foundCount()).isEqualTo(3);
		assertThat(result.createdCount()).isEqualTo(3);
	}

	@Test
	void requestManualRunLogsCrawlProgress(CapturedOutput output) {
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

		service.requestManualRun("rocketsalad");

		assertThat(output).contains("crawl.run.started");
		assertThat(output).contains("crawl.cursor.fetch.succeeded");
		assertThat(output).contains("crawl.product.saved");
		assertThat(output).contains("crawl.run.succeeded");
	}

	@Test
	void requestManualRunAppliesConfiguredDelayBeforeListAndDetailRequests() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		SiteCrawler crawler = new StubSiteCrawler();
		List<Duration> appliedDelays = new ArrayList<>();
		CrawlRunService service = new CrawlRunService(
			siteRepository,
			runRepository,
			productRepository,
			measurementRepository,
			new CrawlerRegistry(List.of(crawler)),
			Duration.ofMillis(1000),
			appliedDelays::add
		);
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		when(siteRepository.findByCode("rocketsalad")).thenReturn(Optional.of(site));
		when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(productRepository.findBySiteAndSourceProductId(site, "521529")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.requestManualRun("rocketsalad");

		assertThat(appliedDelays).containsExactly(Duration.ofMillis(1000), Duration.ofMillis(1000));
	}

	@Test
	void requestManualRunRejectsNewProductWhenNameIsBlank() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		BlankProductCrawler crawler = new BlankProductCrawler();
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
		when(productRepository.findBySiteAndSourceProductId(site, "blank-name")).thenReturn(Optional.empty());

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(result.foundCount()).isEqualTo(1);
		assertThat(result.createdCount()).isZero();
		assertThat(result.updatedCount()).isZero();
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.message()).contains("blank-name");
		assertThat(result.message()).contains("blank product name");
		verify(productRepository, never()).save(any());
	}

	@Test
	void requestManualRunRepairsExistingProductWhenStoredProductIsIncomplete() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductMeasurementRepository measurementRepository = mock(ProductMeasurementRepository.class);
		PagedSiteCrawler crawler = new PagedSiteCrawler();
		CrawlRunService service = new CrawlRunService(
			siteRepository,
			runRepository,
			productRepository,
			measurementRepository,
			new CrawlerRegistry(List.of(crawler))
		);
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		ProductEntity incompleteProduct = ProductEntity.create(site, "top-old");
		when(siteRepository.findByCode("rocketsalad")).thenReturn(Optional.of(site));
		when(runRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(productRepository.findBySiteAndSourceProductId(site, "top-new")).thenReturn(Optional.empty());
		when(productRepository.findBySiteAndSourceProductId(site, "top-old"))
			.thenReturn(Optional.of(incompleteProduct));
		when(productRepository.findBySiteAndSourceProductId(site, "pants-new")).thenReturn(Optional.empty());
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CrawlRunResult result = service.requestManualRun("rocketsalad");

		assertThat(result.foundCount()).isEqualTo(3);
		assertThat(result.createdCount()).isEqualTo(2);
		assertThat(result.updatedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isZero();
		assertThat(crawler.fetchedDetails).containsExactly("top-new", "top-old", "pants-new");
		ArgumentCaptor<ProductEntity> productCaptor = ArgumentCaptor.forClass(ProductEntity.class);
		verify(productRepository, times(3)).save(productCaptor.capture());
		assertThat(productCaptor.getAllValues()).extracting(ProductEntity::name)
			.contains("top-old");
	}

	private static class StubSiteCrawler implements SiteCrawler {

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
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

	private static class PagedSiteCrawler implements SiteCrawler {

		private final List<String> fetchedCursors = new java.util.ArrayList<>();
		private final List<String> fetchedDetails = new java.util.ArrayList<>();

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public List<CrawlCursor> initialCursors() {
			return List.of(new CrawlCursor("TOP:1"), new CrawlCursor("PANTS:1"));
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			fetchedCursors.add(cursor.value());
			if ("TOP:1".equals(cursor.value())) {
				return new CrawlListResult(List.of(summary("top-new")), new CrawlCursor("TOP:2"));
			}
			if ("TOP:2".equals(cursor.value())) {
				return new CrawlListResult(List.of(summary("top-old")), new CrawlCursor("TOP:3"));
			}
			if ("PANTS:1".equals(cursor.value())) {
				return new CrawlListResult(List.of(summary("pants-new")), null);
			}
			throw new AssertionError("Unexpected cursor: " + cursor.value());
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			fetchedDetails.add(productRef.sourceProductId());
			return detail(productRef, ProductAvailability.SOLD_OUT);
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.SOLD_OUT;
		}
	}

	private static class RocketSaladCategoryCrawler implements SiteCrawler {

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			return new CrawlListResult(List.of(
				summary("shirt"),
				summary("pants"),
				summary("outer"),
				summary("women")
			), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			return switch (productRef.sourceProductId()) {
				case "shirt" -> detail(productRef, "Top (대) > 1/2 summershirt (중)");
				case "pants" -> detail(productRef, "Pants (대) > military (중)");
				case "outer" -> detail(productRef, "Outer (대) > western,hippie (중)");
				case "women" -> new CrawledProductDetail(
					productRef,
					"~70`s French Faded Chore Jacket For Women",
					new BigDecimal("55000"),
					null,
					ProductAvailability.AVAILABLE,
					"프렌치 워크웨어 초어 자켓 입니다.",
					URI.create("https://www.rocketsalad.co.kr/shopimages/" + productRef.sourceProductId() + ".jpg"),
					"Womancloth (대) > All Show (중)",
					Map.of("가슴", "48", "기장", "61")
				);
				default -> throw new AssertionError("Unexpected product: " + productRef.sourceProductId());
			};
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.AVAILABLE;
		}
	}

	private static class FailingDetailCrawler implements SiteCrawler {

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			return new CrawlListResult(List.of(summary("fail-1")), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			throw new IllegalStateException("detail exploded");
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.UNKNOWN;
		}
	}

	private static class NeverEndingSiteCrawler implements SiteCrawler {

		private final List<String> fetchedCursors = new java.util.ArrayList<>();

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public List<CrawlCursor> initialCursors() {
			return List.of(new CrawlCursor("TOP:1"));
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			fetchedCursors.add(cursor.value());
			String[] parts = cursor.value().split(":", 2);
			int nextPage = Integer.parseInt(parts[1]) + 1;
			return new CrawlListResult(List.of(summary("top-" + parts[1])), new CrawlCursor(parts[0] + ":" + nextPage));
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			return detail(productRef, ProductAvailability.AVAILABLE);
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.AVAILABLE;
		}
	}

	private static class BlankProductCrawler implements SiteCrawler {

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			CrawledProductRef ref = new CrawledProductRef("blank-name",
				URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=blank-name"));
			return new CrawlListResult(List.of(new CrawledProductSummary(ref, "", null)), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			return new CrawledProductDetail(
				productRef,
				"",
				new BigDecimal("30000"),
				null,
				ProductAvailability.UNKNOWN,
				"detail text",
				null,
				null,
				Map.of()
			);
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.UNKNOWN;
		}
	}

	private static CrawledProductSummary summary(String sourceProductId) {
		CrawledProductRef ref = new CrawledProductRef(sourceProductId,
			URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=" + sourceProductId));
		return new CrawledProductSummary(ref, "fallback",
			URI.create("https://www.rocketsalad.co.kr/shopimages/" + sourceProductId + ".jpg"));
	}

	private static CrawledProductDetail detail(CrawledProductRef productRef, ProductAvailability availability) {
		return new CrawledProductDetail(
			productRef,
			productRef.sourceProductId(),
			new BigDecimal("55000"),
			null,
			availability,
			"Size: 허리 44cm 기장 51cm",
			URI.create("https://www.rocketsalad.co.kr/shopimages/" + productRef.sourceProductId() + ".jpg"),
			"Pants > casual",
			Map.of("허리", "44", "기장", "51")
		);
	}

	private static CrawledProductDetail detail(CrawledProductRef productRef, String sourceCategoryName) {
		return new CrawledProductDetail(
			productRef,
			productRef.sourceProductId(),
			new BigDecimal("55000"),
			null,
			ProductAvailability.AVAILABLE,
			"Size: 허리 44cm 기장 51cm",
			URI.create("https://www.rocketsalad.co.kr/shopimages/" + productRef.sourceProductId() + ".jpg"),
			sourceCategoryName,
			Map.of("허리", "44", "기장", "51")
		);
	}

	private static ProductEntity completeProduct(CrawlSiteEntity site, String sourceProductId) {
		ProductEntity product = ProductEntity.create(site, sourceProductId);
		CrawledProductSummary summary = summary(sourceProductId);
		product.updateFrom(detail(summary.ref(), ProductAvailability.AVAILABLE), summary, Instant.now());
		return product;
	}
}
