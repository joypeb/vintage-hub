package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.crawl.application.CrawlerRegistry;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class ProductAvailabilityCheckService {

	private static final Logger log = LoggerFactory.getLogger(ProductAvailabilityCheckService.class);

	private final ProductRepository productRepository;
	private final CrawlerRegistry crawlerRegistry;
	private final ProductAvailabilityCheckProperties properties;
	private final Clock clock;
	private final AvailabilityCheckSleeper sleeper;

	@Autowired
	public ProductAvailabilityCheckService(ProductRepository productRepository, CrawlerRegistry crawlerRegistry,
			ProductAvailabilityCheckProperties properties) {
		this(productRepository, crawlerRegistry, properties, Clock.systemUTC(), ProductAvailabilityCheckService::sleep);
	}

	ProductAvailabilityCheckService(ProductRepository productRepository, CrawlerRegistry crawlerRegistry,
			ProductAvailabilityCheckProperties properties, Clock clock, AvailabilityCheckSleeper sleeper) {
		this.productRepository = productRepository;
		this.crawlerRegistry = crawlerRegistry;
		this.properties = properties;
		this.clock = clock;
		this.sleeper = sleeper;
	}

	@Transactional
	public ProductAvailabilityCheckResult checkProduct(Long productId) {
		// 관리자 수동 확인은 단일 상품을 찾아 공통 확인 루틴으로 넘긴다.
		log.atInfo()
			.addKeyValue("event", "product.availability.check.requested")
			.addKeyValue("productId", productId)
			.log("product.availability.check.requested");
		ProductEntity product = productRepository.findById(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
		return checkProducts(List.of(product));
	}

	public ProductAvailabilityCheckResult checkDueProducts() {
		return checkDueProducts(properties.batchSize());
	}

	public ProductAvailabilityCheckResult checkDueProducts(int batchSize) {
		int resolvedBatchSize = Math.max(batchSize, 1);
		Instant now = Instant.now(clock);
		// 먼저 확인 주기가 도래한 상품이 있는 사이트만 조회해 불필요한 크롤러 실행을 피한다.
		log.atDebug()
			.addKeyValue("event", "product.availability.due.search.started")
			.addKeyValue("batchSize", resolvedBatchSize)
			.log("product.availability.due.search.started");
		List<String> siteCodes = productRepository.findDueSiteCodesForAvailabilityCheck(now);
		log.atInfo()
			.addKeyValue("event", "product.availability.due.search.completed")
			.addKeyValue("batchSize", resolvedBatchSize)
			.addKeyValue("siteCount", siteCodes.size())
			.log("product.availability.due.search.completed");
		return checkDueProductsBySite(siteCodes, now, resolvedBatchSize);
	}

	private ProductAvailabilityCheckResult checkDueProductsBySite(List<String> siteCodes, Instant now, int batchSize) {
		if (siteCodes.isEmpty()) {
			return new ProductAvailabilityCheckResult(0, 0, 0, 0, 0);
		}
		// 사이트별 병렬 처리로 서로 다른 쇼핑몰 요청이 한 사이트 지연에 묶이지 않게 한다.
		int parallelism = Math.min(properties.maxParallelSites(), siteCodes.size());
		ExecutorService executor = Executors.newFixedThreadPool(parallelism);
		try {
			List<Future<ProductAvailabilityCheckResult>> futures = new ArrayList<>();
			for (String siteCode : siteCodes) {
				// 같은 사이트 내부 상품은 요청 간 딜레이를 지키기 위해 작업 하나에서 순차 처리한다.
				futures.add(executor.submit(() -> checkDueProductsForSite(siteCode, now, batchSize)));
			}
			return aggregate(futures);
		}
		finally {
			executor.shutdown();
		}
	}

	private ProductAvailabilityCheckResult checkDueProductsForSite(String siteCode, Instant now, int batchSize) {
		// 사이트마다 batchSize만큼만 가져와 한 번의 스케줄 실행이 과도하게 길어지지 않게 한다.
		List<ProductEntity> products = productRepository.findDueForAvailabilityCheckBySiteCode(siteCode, now,
			PageRequest.of(0, batchSize));
		log.atInfo()
			.addKeyValue("event", "product.availability.site.due.search.completed")
			.addKeyValue("siteCode", siteCode)
			.addKeyValue("batchSize", batchSize)
			.addKeyValue("productCount", products.size())
			.log("product.availability.site.due.search.completed");
		return checkProducts(products);
	}

	private ProductAvailabilityCheckResult aggregate(List<Future<ProductAvailabilityCheckResult>> futures) {
		AvailabilityCheckCounts counts = new AvailabilityCheckCounts();
		for (Future<ProductAvailabilityCheckResult> future : futures) {
			// 사이트별 결과를 전체 실행 결과로 합산한다.
			ProductAvailabilityCheckResult result = await(future);
			counts.add(result);
		}
		return counts.toResult();
	}

	private ProductAvailabilityCheckResult await(Future<ProductAvailabilityCheckResult> future) {
		try {
			return future.get();
		}
		catch (InterruptedException exception) {
			// 스케줄러 중단 신호를 잃지 않도록 인터럽트 상태를 복원한다.
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Product availability check interrupted", exception);
		}
		catch (ExecutionException exception) {
			throw new IllegalStateException("Product availability site batch failed", exception.getCause());
		}
	}

	private ProductAvailabilityCheckResult checkProducts(List<ProductEntity> products) {
		AvailabilityCheckCounts counts = new AvailabilityCheckCounts();
		for (ProductEntity product : products) {
			// 외부 사이트에 대한 단건 요청 사이에는 설정된 지연 시간을 적용한다.
			delayBeforeRequest(product);
			checkOneProduct(product, counts);
		}
		return counts.toResult();
	}

	private void checkOneProduct(ProductEntity product, AvailabilityCheckCounts counts) {
		Instant checkedAt = Instant.now(clock);
		try {
			// 상품이 속한 사이트의 크롤러를 찾아 현재 상세 페이지의 재고 상태만 다시 확인한다.
			SiteCrawler crawler = crawlerRegistry.requireBySiteCode(product.site().code());
			ProductAvailability availability = crawler.checkAvailability(
				new CrawlTargetSite(product.site().code(), product.site().baseUrl()),
				new CrawledProductRef(product.sourceProductId(), URI.create(product.detailUrl()))
			);
			// 확인 결과에 따라 다음 확인 시점을 다르게 잡아 품절/오류 상품을 더 자주 볼 수 있다.
			product.markAvailabilityCheckSucceeded(availability, checkedAt, nextCheckAt(availability, checkedAt));
			productRepository.save(product);
			counts.record(availability);
			log.atInfo()
				.addKeyValue("event", "product.availability.check.succeeded")
				.addKeyValue("productId", product.id())
				.addKeyValue("siteCode", product.site().code())
				.addKeyValue("sourceProductId", product.sourceProductId())
				.addKeyValue("availability", availability)
				.log("product.availability.check.succeeded");
		}
		catch (RuntimeException exception) {
			// 확인 실패도 상태와 다음 재시도 시간을 저장해 같은 상품이 즉시 반복 실패하지 않게 한다.
			product.markAvailabilityCheckFailed(checkedAt, nextCheckAt(ProductAvailability.CHECK_FAILED, checkedAt));
			productRepository.save(product);
			counts.recordFailure();
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "product.availability.check.failed")
				.addKeyValue("productId", product.id())
				.addKeyValue("siteCode", product.site().code())
				.addKeyValue("sourceProductId", product.sourceProductId())
				.addKeyValue("reason", failureMessage(exception))
				.log("product.availability.check.failed");
		}
	}

	private Instant nextCheckAt(ProductAvailability availability, Instant checkedAt) {
		return checkedAt.plus(ttlFor(availability));
	}

	private Duration ttlFor(ProductAvailability availability) {
		// 재고 상태별 TTL은 운영 설정으로 조절한다.
		return switch (availability) {
			case AVAILABLE -> properties.availableTtl();
			case SOLD_OUT -> properties.soldOutTtl();
			case UNKNOWN -> properties.unknownTtl();
			case CHECK_FAILED -> properties.checkFailedTtl();
		};
	}

	private void delayBeforeRequest(ProductEntity product) {
		Duration requestDelay = properties.requestDelay();
		if (requestDelay.isZero() || requestDelay.isNegative()) {
			// 지연이 0 이하이면 테스트와 개발 환경에서 즉시 실행한다.
			return;
		}
		log.atDebug()
			.addKeyValue("event", "product.availability.request.delay")
			.addKeyValue("productId", product.id())
			.addKeyValue("delayMs", requestDelay.toMillis())
			.log("product.availability.request.delay");
		sleeper.sleep(requestDelay);
	}

	private String failureMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Product availability check interrupted", exception);
		}
	}

	interface AvailabilityCheckSleeper {

		void sleep(Duration duration);
	}

	private static class AvailabilityCheckCounts {

		private int checkedCount;
		private int availableCount;
		private int soldOutCount;
		private int unknownCount;
		private int failedCount;

		private void record(ProductAvailability availability) {
			// 성공한 확인은 전체 checkedCount와 상태별 카운트를 동시에 증가시킨다.
			checkedCount++;
			switch (availability) {
				case AVAILABLE -> availableCount++;
				case SOLD_OUT -> soldOutCount++;
				case UNKNOWN -> unknownCount++;
				case CHECK_FAILED -> failedCount++;
			}
		}

		private void recordFailure() {
			// 예외가 난 상품도 확인 시도 수에는 포함한다.
			checkedCount++;
			failedCount++;
		}

		private void add(ProductAvailabilityCheckResult result) {
			// 병렬 사이트 작업에서 반환된 누적치를 전체 카운터에 더한다.
			checkedCount += result.checkedCount();
			availableCount += result.availableCount();
			soldOutCount += result.soldOutCount();
			unknownCount += result.unknownCount();
			failedCount += result.failedCount();
		}

		private ProductAvailabilityCheckResult toResult() {
			return new ProductAvailabilityCheckResult(checkedCount, availableCount, soldOutCount, unknownCount,
				failedCount);
		}
	}
}
