package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CrawlRunService {

	private static final Logger log = LoggerFactory.getLogger(CrawlRunService.class);
	private static final int MAX_PAGES_PER_INITIAL_CURSOR = 3;

	private final CrawlSiteRepository siteRepository;
	private final CrawlRunRepository runRepository;
	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;
	private final CrawlerRegistry crawlerRegistry;
	private final Duration requestDelay;
	private final CrawlRequestSleeper requestSleeper;
	private final CrawlRunLifecycleService lifecycleService;

	@Autowired
	public CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry,
			CrawlRunLifecycleService lifecycleService,
			@Value("${vintage-hub.crawl.request-delay-ms:1000}") long requestDelayMs) {
		this(siteRepository, runRepository, productRepository, measurementRepository, crawlerRegistry,
			Duration.ofMillis(requestDelayMs), CrawlRunService::sleep, lifecycleService);
	}

	CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry) {
		this(siteRepository, runRepository, productRepository, measurementRepository, crawlerRegistry,
			Duration.ZERO, duration -> {
			}, null);
	}

	CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry, Duration requestDelay, CrawlRequestSleeper requestSleeper) {
		this(siteRepository, runRepository, productRepository, measurementRepository, crawlerRegistry, requestDelay,
			requestSleeper, null);
	}

	CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry, Duration requestDelay, CrawlRequestSleeper requestSleeper,
			CrawlRunLifecycleService lifecycleService) {
		this.siteRepository = siteRepository;
		this.runRepository = runRepository;
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
		this.crawlerRegistry = crawlerRegistry;
		this.requestDelay = requestDelay;
		this.requestSleeper = requestSleeper;
		this.lifecycleService = lifecycleService;
	}

	@Transactional
	public CrawlRunResult requestManualRun(String siteCode) {
		return requestRun(siteCode, CrawlRunEntity::manual);
	}

	@Transactional
	public CrawlRunResult requestScheduledRun(String siteCode) {
		return requestRun(siteCode, CrawlRunEntity::scheduled);
	}

	@Transactional
	public CrawlRunResult executeRun(Long runId, String siteCode) {
		// 비동기 요청 서비스가 미리 만든 실행 건을 실제 크롤링 작업으로 전환한다.
		log.atInfo()
			.addKeyValue("event", "crawl.run.started")
			.addKeyValue("runId", runId)
			.addKeyValue("siteCode", siteCode)
			.addKeyValue("requestDelayMs", requestDelay.toMillis())
			.log("crawl.run.started");
		CrawlSiteEntity site = siteRepository.findByCode(siteCode)
			.orElseThrow(() -> new IllegalArgumentException("Crawl site not found: " + siteCode));
		SiteCrawler crawler = crawlerRegistry.requireBySiteCode(siteCode);

		try {
			// 상품 저장 로직은 실행 방식과 무관하게 동일하게 사용하고, 결과 카운트만 실행 건에 반영한다.
			CrawlCounts counts = saveProducts(site, crawler, runId);
			String message = successMessage(counts);
			lifecycleService.markSucceeded(runId, counts.foundCount, counts.createdCount, counts.updatedCount,
				counts.failedCount, message);
			site.markCrawled(counts.createdCount + counts.updatedCount > 0);
			log.atInfo()
				.addKeyValue("event", "crawl.run.succeeded")
				.addKeyValue("runId", runId)
				.addKeyValue("siteCode", site.code())
				.addKeyValue("foundCount", counts.foundCount)
				.addKeyValue("createdCount", counts.createdCount)
				.addKeyValue("updatedCount", counts.updatedCount)
				.addKeyValue("failedCount", counts.failedCount)
				.addKeyValue("resultMessage", message)
				.log("crawl.run.succeeded");
			return new CrawlRunResult(site.code(), "SUCCEEDED", counts.foundCount, counts.createdCount,
				counts.updatedCount, counts.failedCount, message);
		}
		catch (RuntimeException exception) {
			lifecycleService.markFailed(runId, failureMessage(exception));
			log.atError()
				.setCause(exception)
				.addKeyValue("event", "crawl.run.failed")
				.addKeyValue("runId", runId)
				.addKeyValue("siteCode", siteCode)
				.addKeyValue("reason", failureMessage(exception))
				.log("crawl.run.failed");
			throw exception;
		}
	}

	private CrawlRunResult requestRun(String siteCode, CrawlRunFactory runFactory) {
		// 동기 실행 경로에서는 실행 건 생성부터 상품 수집까지 한 트랜잭션 흐름에서 처리한다.
		log.atInfo()
			.addKeyValue("event", "crawl.run.started")
			.addKeyValue("siteCode", siteCode)
			.addKeyValue("requestDelayMs", requestDelay.toMillis())
			.log("crawl.run.started");
		CrawlSiteEntity site = siteRepository.findByCode(siteCode)
			.orElseThrow(() -> new IllegalArgumentException("Crawl site not found: " + siteCode));
		SiteCrawler crawler = crawlerRegistry.requireBySiteCode(siteCode);
		CrawlRunEntity run = runRepository.save(runFactory.create(site));
		run.markRunning();

		try {
			// 수동/스케줄 실행은 생성 방식만 다르고 이후 저장 로직은 공유한다.
			CrawlCounts counts = saveProducts(site, crawler);
			String message = successMessage(counts);
			run.markSucceeded(counts.foundCount, counts.createdCount, counts.updatedCount, counts.failedCount,
				message);
			site.markCrawled(counts.createdCount + counts.updatedCount > 0);
			log.atInfo()
				.addKeyValue("event", "crawl.run.succeeded")
				.addKeyValue("siteCode", site.code())
				.addKeyValue("foundCount", counts.foundCount)
				.addKeyValue("createdCount", counts.createdCount)
				.addKeyValue("updatedCount", counts.updatedCount)
				.addKeyValue("failedCount", counts.failedCount)
				.addKeyValue("resultMessage", message)
				.log("crawl.run.succeeded");
			return new CrawlRunResult(site.code(), run.status().name(), counts.foundCount, counts.createdCount,
				counts.updatedCount, counts.failedCount, run.message());
		}
		catch (RuntimeException exception) {
			run.markFailed(exception.getMessage());
			log.atError()
				.setCause(exception)
				.addKeyValue("event", "crawl.run.failed")
				.addKeyValue("siteCode", siteCode)
				.addKeyValue("reason", failureMessage(exception))
				.log("crawl.run.failed");
			throw exception;
		}
	}

	@FunctionalInterface
	private interface CrawlRunFactory {
		CrawlRunEntity create(CrawlSiteEntity site);
	}

	private CrawlCounts saveProducts(CrawlSiteEntity site, SiteCrawler crawler) {
		return saveProducts(site, crawler, null);
	}

	private CrawlCounts saveProducts(CrawlSiteEntity site, SiteCrawler crawler, Long runId) {
		CrawlCounts counts = new CrawlCounts();
		CrawlTargetSite targetSite = new CrawlTargetSite(site.code(), site.baseUrl());
		// 사이트별 크롤러가 카테고리나 첫 페이지 같은 시작 커서를 결정한다.
		List<CrawlCursor> initialCursors = crawler.initialCursors();
		log.atInfo()
			.addKeyValue("event", "crawl.cursors.resolved")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("cursorCount", initialCursors.size())
			.addKeyValue("cursors", initialCursors)
			.log("crawl.cursors.resolved");

		if (initialCursors.isEmpty()) {
			// 커서가 없는 사이트는 기본 목록 URL 하나만 순회한다.
			collectCursor(site, crawler, targetSite, null, counts, runId);
			return counts;
		}
		for (CrawlCursor initialCursor : initialCursors) {
			collectCursor(site, crawler, targetSite, initialCursor, counts, runId);
		}
		return counts;
	}

	private void collectCursor(CrawlSiteEntity site, SiteCrawler crawler, CrawlTargetSite targetSite,
			CrawlCursor initialCursor, CrawlCounts counts, Long runId) {
		CrawlCursor cursor = initialCursor;
		// 신규 상품 수집 목적이므로 시작 커서당 최대 페이지 수를 제한해 외부 사이트 부하를 줄인다.
		for (int pageCount = 0; pageCount < MAX_PAGES_PER_INITIAL_CURSOR; pageCount++) {
			log.atInfo()
				.addKeyValue("event", "crawl.cursor.fetch.started")
				.addKeyValue("siteCode", site.code())
				.addKeyValue("cursor", cursorValue(cursor))
				.addKeyValue("pageAttempt", pageCount + 1)
				.addKeyValue("maxPageAttempt", MAX_PAGES_PER_INITIAL_CURSOR)
				.log("crawl.cursor.fetch.started");
			delayBeforeRequest(site.code(), "list", cursorValue(cursor));
			CrawlListResult listResult = crawler.fetchList(targetSite, cursor);
			log.atInfo()
				.addKeyValue("event", "crawl.cursor.fetch.succeeded")
				.addKeyValue("siteCode", site.code())
				.addKeyValue("cursor", cursorValue(cursor))
				.addKeyValue("productCount", listResult.products().size())
				.addKeyValue("nextCursor", cursorValue(listResult.nextCursor()))
				.log("crawl.cursor.fetch.succeeded");
			boolean stopPaging = savePageProducts(site, crawler, targetSite, listResult.products(), counts, runId);
			if (stopPaging) {
				// 이미 저장된 정상 상품을 만나면 최신순 목록의 이후 페이지도 기존 데이터로 간주한다.
				logCursorStopped(site, cursor, "existing-product");
				return;
			}
			if (listResult.nextCursor() == null) {
				// 다음 커서가 없으면 사이트가 제공한 마지막 페이지로 본다.
				logCursorStopped(site, cursor, "no-next-cursor");
				return;
			}
			if (listResult.products().isEmpty()) {
				// 빈 목록은 다음 페이지를 더 호출해도 신규 상품이 없을 가능성이 높다.
				logCursorStopped(site, cursor, "empty-page");
				return;
			}
			cursor = listResult.nextCursor();
		}
	}

	private boolean savePageProducts(CrawlSiteEntity site, SiteCrawler crawler, CrawlTargetSite targetSite,
			List<CrawledProductSummary> summaries, CrawlCounts counts, Long runId) {
		Instant collectedAt = Instant.now();
		for (CrawledProductSummary summary : summaries) {
			// 목록에서 발견한 개수는 상세 수집 성공 여부와 별도로 집계한다.
			counts.foundCount++;
			String sourceProductId = summary.ref().sourceProductId();
			markProgress(runId, counts, "Processing " + sourceProductId);
			log.atDebug()
				.addKeyValue("event", "crawl.product.processing")
				.addKeyValue("siteCode", site.code())
				.addKeyValue("sourceProductId", sourceProductId)
				.addKeyValue("detailUrl", summary.ref().detailUrl())
				.log("crawl.product.processing");
			var existingProduct = productRepository.findBySiteAndSourceProductId(site, summary.ref().sourceProductId());
			if (existingProduct.isPresent() && !existingProduct.get().needsCrawlRepair()) {
				// 정상 저장된 기존 상품은 최신순 경계로 사용해 불필요한 상세 요청을 중단한다.
				log.atInfo()
					.addKeyValue("event", "crawl.product.skipped")
					.addKeyValue("siteCode", site.code())
					.addKeyValue("sourceProductId", sourceProductId)
					.addKeyValue("reason", "already-exists")
					.addKeyValue("action", "stop-paging")
					.log("crawl.product.skipped");
				return true;
			}
			try {
				if (existingProduct.isPresent()) {
					// 과거 수집 데이터가 불완전하면 같은 상품이라도 상세를 다시 읽어 보수한다.
					log.atInfo()
						.addKeyValue("event", "crawl.product.repair.started")
						.addKeyValue("siteCode", site.code())
						.addKeyValue("sourceProductId", sourceProductId)
						.log("crawl.product.repair.started");
				}
				delayBeforeRequest(site.code(), "detail", sourceProductId);
				CrawledProductDetail detail = crawler.fetchDetail(targetSite, summary.ref());
				validateProduct(detail, summary);
				boolean exists = existingProduct.isPresent();
				// 기존 상품은 갱신하고, 신규 상품은 사이트+원본 ID 기준으로 새 엔티티를 만든다.
				ProductEntity product = existingProduct
					.orElseGet(() -> ProductEntity.create(site, detail.ref().sourceProductId()));
				product.updateFrom(detail, summary, collectedAt);
				ProductEntity savedProduct = productRepository.save(product);
				replaceMeasurements(savedProduct, detail);
				if (exists) {
					// 복구 대상 상품을 고쳤다면 이후 페이지는 기존 데이터 영역으로 간주해 중단한다.
					counts.updatedCount++;
					logProductSaved(site, sourceProductId, "updated", detail, summary);
					markProgress(runId, counts, "Updated " + sourceProductId);
					return true;
				}
				else {
					counts.createdCount++;
					logProductSaved(site, sourceProductId, "created", detail, summary);
					markProgress(runId, counts, "Created " + sourceProductId);
				}
			}
			catch (RuntimeException exception) {
				// 개별 상품 실패는 전체 크롤링 실패로 올리지 않고 실패 카운트와 메시지에 누적한다.
				counts.failedCount++;
				String failureMessage = failureMessage(exception);
				counts.failureReasons.add(sourceProductId + ": " + failureMessage);
				markProgress(runId, counts, "Failed " + sourceProductId + ": " + failureMessage);
				log.atWarn()
					.setCause(exception)
					.addKeyValue("event", "crawl.product.failed")
					.addKeyValue("siteCode", site.code())
					.addKeyValue("sourceProductId", sourceProductId)
					.addKeyValue("reason", failureMessage)
					.log("crawl.product.failed");
			}
		}
		return false;
	}

	private void markProgress(Long runId, CrawlCounts counts, String message) {
		if (runId == null || lifecycleService == null) {
			// 동기 실행이나 단위 테스트 경로에서는 별도 진행 이벤트가 없을 수 있다.
			return;
		}
		lifecycleService.markProgress(runId, counts.foundCount, counts.createdCount, counts.updatedCount,
			counts.failedCount, message);
	}

	private void replaceMeasurements(ProductEntity product, CrawledProductDetail detail) {
		// 크롤러가 읽은 최신 실측값을 기준으로 자동 수집 실측 정보를 통째로 교체한다.
		measurementRepository.deleteByProduct(product);
		Map<String, String> measurements = detail.measurements();
		if (measurements == null || measurements.isEmpty()) {
			// 실측 정보가 없으면 기존 자동 실측값만 제거하고 종료한다.
			return;
		}
		List<ProductMeasurementEntity> entities = new ArrayList<>();
		for (Map.Entry<String, String> entry : measurements.entrySet()) {
			entities.add(ProductMeasurementEntity.automatic(product, entry.getKey(), new BigDecimal(entry.getValue()),
				detail.description()));
		}
		measurementRepository.saveAll(entities);
	}

	private void delayBeforeRequest(String siteCode, String requestType, String target) {
		if (requestDelay == null || requestDelay.isZero() || requestDelay.isNegative()) {
			// 설정상 지연이 없으면 테스트와 로컬 실행을 빠르게 유지한다.
			return;
		}
		log.atDebug()
			.addKeyValue("event", "crawl.request.delay")
			.addKeyValue("siteCode", siteCode)
			.addKeyValue("requestType", requestType)
			.addKeyValue("target", target)
			.addKeyValue("delayMs", requestDelay.toMillis())
			.log("crawl.request.delay");
		requestSleeper.sleep(requestDelay);
	}

	private void logCursorStopped(CrawlSiteEntity site, CrawlCursor cursor, String reason) {
		log.atInfo()
			.addKeyValue("event", "crawl.cursor.stopped")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("cursor", cursorValue(cursor))
			.addKeyValue("reason", reason)
			.log("crawl.cursor.stopped");
	}

	private void logProductSaved(CrawlSiteEntity site, String sourceProductId, String action,
			CrawledProductDetail detail, CrawledProductSummary summary) {
		log.atInfo()
			.addKeyValue("event", "crawl.product.saved")
			.addKeyValue("siteCode", site.code())
			.addKeyValue("sourceProductId", sourceProductId)
			.addKeyValue("action", action)
			.addKeyValue("name", firstText(detail.name(), summary.name()))
			.addKeyValue("price", detail.originalPrice())
			.addKeyValue("stockStatus", detail.availability())
			.addKeyValue("measurementCount", measurementCount(detail))
			.log("crawl.product.saved");
	}

	private void validateProduct(CrawledProductDetail detail, CrawledProductSummary summary) {
		if (firstText(detail.name(), summary.name()) == null) {
			// 상품명은 목록 또는 상세 중 한쪽에서 반드시 확보되어야 검색/표시가 가능하다.
			throw new IllegalStateException("blank product name");
		}
	}

	private String successMessage(CrawlCounts counts) {
		if (counts.failureReasons.isEmpty()) {
			return "Crawl run completed.";
		}
		// DB 메시지 컬럼이 과도하게 커지지 않도록 실패 사유를 제한 길이로 보관한다.
		return truncate("Crawl run completed with failures: " + String.join("; ", counts.failureReasons), 1000);
	}

	private String failureMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}

	private String truncate(String value, int maxLength) {
		if (value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private String firstText(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	private int measurementCount(CrawledProductDetail detail) {
		Map<String, String> measurements = detail.measurements();
		return measurements == null ? 0 : measurements.size();
	}

	private String cursorValue(CrawlCursor cursor) {
		return cursor == null ? "<default>" : cursor.value();
	}

	private static void sleep(Duration duration) {
		try {
			Thread.sleep(duration.toMillis());
		}
		catch (InterruptedException exception) {
			// 인터럽트 상태를 복원해 상위 실행기가 중단 신호를 감지할 수 있게 한다.
			Thread.currentThread().interrupt();
			throw new IllegalStateException("crawl request delay interrupted", exception);
		}
	}

	@FunctionalInterface
	interface CrawlRequestSleeper {
		void sleep(Duration duration);
	}

	private static class CrawlCounts {
		private int foundCount;
		private int createdCount;
		private int updatedCount;
		private int failedCount;
		private final List<String> failureReasons = new ArrayList<>();
	}
}
