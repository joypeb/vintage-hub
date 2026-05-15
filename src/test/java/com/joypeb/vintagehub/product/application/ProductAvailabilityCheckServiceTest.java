package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.crawl.application.CrawlerRegistry;
import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductAvailabilityCheckServiceTest {

	private static final Instant NOW = Instant.parse("2026-05-15T01:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void checkProductUpdatesAvailabilityAndNextCheckTime() {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductEntity product = product("521529", ProductAvailability.SOLD_OUT);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ProductAvailabilityCheckService service = service(productRepository,
			new AvailabilityCrawler(Map.of("521529", ProductAvailability.AVAILABLE)));

		ProductAvailabilityCheckResult result = service.checkProduct(1L);

		assertThat(result.checkedCount()).isEqualTo(1);
		assertThat(result.availableCount()).isEqualTo(1);
		assertThat(result.failedCount()).isZero();
		assertThat(product.stockStatus()).isEqualTo(ProductAvailability.AVAILABLE);
		assertThat(product.availabilityCheckedAt()).isEqualTo(NOW);
		assertThat(product.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofHours(6)));
		verify(productRepository).save(product);
	}

	@Test
	void checkProductThrowsNotFoundForUnknownProductId() {
		ProductRepository productRepository = mock(ProductRepository.class);
		when(productRepository.findById(99L)).thenReturn(Optional.empty());
		ProductAvailabilityCheckService service = service(productRepository,
			new AvailabilityCrawler(Map.of("unused", ProductAvailability.AVAILABLE)));

		assertThatThrownBy(() -> service.checkProduct(99L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Product not found: 99");
	}

	@Test
	void checkDueProductsUsesRepositorySelectedProducts() {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductEntity dueProduct = product("due", ProductAvailability.UNKNOWN);
		when(productRepository.findDueForAvailabilityCheck(NOW, PageRequest.of(0, 3)))
			.thenReturn(List.of(dueProduct));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ProductAvailabilityCheckService service = service(productRepository,
			new AvailabilityCrawler(Map.of("due", ProductAvailability.SOLD_OUT)));

		ProductAvailabilityCheckResult result = service.checkDueProducts(3);

		assertThat(result.checkedCount()).isEqualTo(1);
		assertThat(result.soldOutCount()).isEqualTo(1);
		assertThat(dueProduct.stockStatus()).isEqualTo(ProductAvailability.SOLD_OUT);
		assertThat(dueProduct.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		verify(productRepository).findDueForAvailabilityCheck(NOW, PageRequest.of(0, 3));
	}

	@Test
	void crawlerFailureMarksProductAsCheckFailed() {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductEntity product = product("fail", ProductAvailability.AVAILABLE);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ProductAvailabilityCheckService service = service(productRepository, new FailingAvailabilityCrawler());

		ProductAvailabilityCheckResult result = service.checkProduct(1L);

		assertThat(result.checkedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(product.stockStatus()).isEqualTo(ProductAvailability.CHECK_FAILED);
		assertThat(product.availabilityCheckedAt()).isEqualTo(NOW);
		assertThat(product.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
		verify(productRepository).save(product);
	}

	@Test
	void statusSpecificTtlsAreApplied() {
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductEntity available = product("available", ProductAvailability.UNKNOWN);
		ProductEntity soldOut = product("sold-out", ProductAvailability.UNKNOWN);
		ProductEntity unknown = product("unknown", ProductAvailability.AVAILABLE);
		ProductEntity checkFailed = product("check-failed", ProductAvailability.AVAILABLE);
		when(productRepository.findDueForAvailabilityCheck(NOW, PageRequest.of(0, 4)))
			.thenReturn(List.of(available, soldOut, unknown, checkFailed));
		when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		ProductAvailabilityCheckService service = service(productRepository, new AvailabilityCrawler(Map.of(
			"available", ProductAvailability.AVAILABLE,
			"sold-out", ProductAvailability.SOLD_OUT,
			"unknown", ProductAvailability.UNKNOWN,
			"check-failed", ProductAvailability.CHECK_FAILED
		)));

		ProductAvailabilityCheckResult result = service.checkDueProducts(4);

		assertThat(result.checkedCount()).isEqualTo(4);
		assertThat(result.availableCount()).isEqualTo(1);
		assertThat(result.soldOutCount()).isEqualTo(1);
		assertThat(result.unknownCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(available.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofHours(6)));
		assertThat(soldOut.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(unknown.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(checkFailed.availabilityNextCheckAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
	}

	private ProductAvailabilityCheckService service(ProductRepository productRepository, SiteCrawler crawler) {
		ProductAvailabilityCheckProperties properties = new ProductAvailabilityCheckProperties(true,
			Duration.ofMinutes(10), 20, Duration.ZERO, Duration.ofHours(6), Duration.ofDays(7), Duration.ofHours(1),
			Duration.ofHours(2));
		return new ProductAvailabilityCheckService(productRepository, new CrawlerRegistry(List.of(crawler)), properties,
			CLOCK, duration -> {
			});
	}

	private ProductEntity product(String sourceProductId, ProductAvailability availability) {
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드",
			URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		ProductEntity product = ProductEntity.create(site, sourceProductId);
		CrawledProductRef ref = new CrawledProductRef(sourceProductId,
			URI.create("https://www.rocketsalad.co.kr/product/" + sourceProductId));
		product.updateFrom(
			new CrawledProductDetail(ref, "Vintage Denim", new BigDecimal("55000"), null, availability, "desc", null,
				"Bottom", Map.of()),
			new CrawledProductSummary(ref, "Vintage Denim", null),
			NOW.minus(Duration.ofDays(1)),
			NOW
		);
		return product;
	}

	private static class AvailabilityCrawler implements SiteCrawler {

		private final Map<String, ProductAvailability> availabilityBySourceProductId;

		private AvailabilityCrawler(Map<String, ProductAvailability> availabilityBySourceProductId) {
			this.availabilityBySourceProductId = availabilityBySourceProductId;
		}

		@Override
		public String siteCode() {
			return "rocketsalad";
		}

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			return new CrawlListResult(List.of(), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			throw new UnsupportedOperationException("fetchDetail is not used by availability checks");
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return availabilityBySourceProductId.get(productRef.sourceProductId());
		}
	}

	private static class FailingAvailabilityCrawler extends AvailabilityCrawler {

		private FailingAvailabilityCrawler() {
			super(Map.of());
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			throw new IllegalStateException("availability exploded");
		}
	}
}
