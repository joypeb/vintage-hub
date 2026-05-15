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

	@Autowired
	public CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry,
			@Value("${vintage-hub.crawl.request-delay-ms:1000}") long requestDelayMs) {
		this(siteRepository, runRepository, productRepository, measurementRepository, crawlerRegistry,
			Duration.ofMillis(requestDelayMs), CrawlRunService::sleep);
	}

	CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry) {
		this(siteRepository, runRepository, productRepository, measurementRepository, crawlerRegistry,
			Duration.ZERO, duration -> {
			});
	}

	CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry, Duration requestDelay, CrawlRequestSleeper requestSleeper) {
		this.siteRepository = siteRepository;
		this.runRepository = runRepository;
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
		this.crawlerRegistry = crawlerRegistry;
		this.requestDelay = requestDelay;
		this.requestSleeper = requestSleeper;
	}

	@Transactional
	public CrawlRunResult requestManualRun(String siteCode) {
		log.info("Crawl run started: siteCode={} requestDelayMs={}", siteCode, requestDelay.toMillis());
		CrawlSiteEntity site = siteRepository.findByCode(siteCode)
			.orElseThrow(() -> new IllegalArgumentException("Crawl site not found: " + siteCode));
		SiteCrawler crawler = crawlerRegistry.requireBySiteCode(siteCode);
		CrawlRunEntity run = runRepository.save(CrawlRunEntity.manual(site));
		run.markRunning();

		try {
			CrawlCounts counts = saveProducts(site, crawler);
			String message = successMessage(counts);
			run.markSucceeded(counts.foundCount, counts.createdCount, counts.updatedCount, counts.failedCount,
				message);
			site.markCrawled(counts.createdCount + counts.updatedCount > 0);
			log.info("Crawl run succeeded: siteCode={} found={} created={} updated={} failed={} message={}",
				site.code(), counts.foundCount, counts.createdCount, counts.updatedCount, counts.failedCount, message);
			return new CrawlRunResult(site.code(), run.status().name(), counts.foundCount, counts.createdCount,
				counts.updatedCount, counts.failedCount, run.message());
		}
		catch (RuntimeException exception) {
			run.markFailed(exception.getMessage());
			log.error("Crawl run failed: siteCode={} reason={}", siteCode, failureMessage(exception), exception);
			throw exception;
		}
	}

	private CrawlCounts saveProducts(CrawlSiteEntity site, SiteCrawler crawler) {
		CrawlCounts counts = new CrawlCounts();
		CrawlTargetSite targetSite = new CrawlTargetSite(site.code(), site.baseUrl());
		List<CrawlCursor> initialCursors = crawler.initialCursors();
		log.info("Crawl initial cursors resolved: siteCode={} cursorCount={} cursors={}", site.code(),
			initialCursors.size(), initialCursors);

		if (initialCursors.isEmpty()) {
			collectCursor(site, crawler, targetSite, null, counts);
			return counts;
		}
		for (CrawlCursor initialCursor : initialCursors) {
			collectCursor(site, crawler, targetSite, initialCursor, counts);
		}
		return counts;
	}

	private void collectCursor(CrawlSiteEntity site, SiteCrawler crawler, CrawlTargetSite targetSite,
			CrawlCursor initialCursor, CrawlCounts counts) {
		CrawlCursor cursor = initialCursor;
		for (int pageCount = 0; pageCount < MAX_PAGES_PER_INITIAL_CURSOR; pageCount++) {
			log.info("Crawl cursor fetch started: siteCode={} cursor={} pageAttempt={}/{}", site.code(),
				cursorValue(cursor), pageCount + 1, MAX_PAGES_PER_INITIAL_CURSOR);
			delayBeforeRequest(site.code(), "list", cursorValue(cursor));
			CrawlListResult listResult = crawler.fetchList(targetSite, cursor);
			log.info("Crawl cursor fetched: siteCode={} cursor={} productCount={} nextCursor={}", site.code(),
				cursorValue(cursor), listResult.products().size(), cursorValue(listResult.nextCursor()));
			boolean stopPaging = savePageProducts(site, crawler, targetSite, listResult.products(), counts);
			if (stopPaging) {
				log.info("Crawl cursor stopped: siteCode={} cursor={} reason=existing-product", site.code(),
					cursorValue(cursor));
				return;
			}
			if (listResult.nextCursor() == null) {
				log.info("Crawl cursor stopped: siteCode={} cursor={} reason=no-next-cursor", site.code(),
					cursorValue(cursor));
				return;
			}
			if (listResult.products().isEmpty()) {
				log.info("Crawl cursor stopped: siteCode={} cursor={} reason=empty-page", site.code(),
					cursorValue(cursor));
				return;
			}
			cursor = listResult.nextCursor();
		}
	}

	private boolean savePageProducts(CrawlSiteEntity site, SiteCrawler crawler, CrawlTargetSite targetSite,
			List<CrawledProductSummary> summaries, CrawlCounts counts) {
		Instant collectedAt = Instant.now();
		for (CrawledProductSummary summary : summaries) {
			counts.foundCount++;
			String sourceProductId = summary.ref().sourceProductId();
			log.debug("Crawl product processing: siteCode={} sourceProductId={} detailUrl={}", site.code(),
				sourceProductId, summary.ref().detailUrl());
			var existingProduct = productRepository.findBySiteAndSourceProductId(site, summary.ref().sourceProductId());
			if (existingProduct.isPresent() && !existingProduct.get().needsCrawlRepair()) {
				log.info("Crawl product already exists: siteCode={} sourceProductId={} action=stop-paging",
					site.code(), sourceProductId);
				return true;
			}
			try {
				if (existingProduct.isPresent()) {
					log.info("Crawl product repair started: siteCode={} sourceProductId={}", site.code(),
						sourceProductId);
				}
				delayBeforeRequest(site.code(), "detail", sourceProductId);
				CrawledProductDetail detail = crawler.fetchDetail(targetSite, summary.ref());
				validateProduct(detail, summary);
				boolean exists = existingProduct.isPresent();
				ProductEntity product = existingProduct
					.orElseGet(() -> ProductEntity.create(site, detail.ref().sourceProductId()));
				product.updateFrom(detail, summary, collectedAt);
				ProductEntity savedProduct = productRepository.save(product);
				replaceMeasurements(savedProduct, detail);
				if (exists) {
					counts.updatedCount++;
					log.info("Crawl product saved: siteCode={} sourceProductId={} action=updated name=\"{}\" price={} stockStatus={} measurementCount={}",
						site.code(), sourceProductId, firstText(detail.name(), summary.name()), detail.originalPrice(),
						detail.availability(), measurementCount(detail));
					return true;
				}
				else {
					counts.createdCount++;
					log.info("Crawl product saved: siteCode={} sourceProductId={} action=created name=\"{}\" price={} stockStatus={} measurementCount={}",
						site.code(), sourceProductId, firstText(detail.name(), summary.name()), detail.originalPrice(),
						detail.availability(), measurementCount(detail));
				}
			}
			catch (RuntimeException exception) {
				counts.failedCount++;
				String failureMessage = failureMessage(exception);
				counts.failureReasons.add(sourceProductId + ": " + failureMessage);
				log.warn("Crawl product failed: siteCode={} sourceProductId={} reason={}", site.code(),
					sourceProductId, failureMessage, exception);
			}
		}
		return false;
	}

	private void replaceMeasurements(ProductEntity product, CrawledProductDetail detail) {
		measurementRepository.deleteByProduct(product);
		Map<String, String> measurements = detail.measurements();
		if (measurements == null || measurements.isEmpty()) {
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
			return;
		}
		log.debug("Crawl request delay: siteCode={} requestType={} target={} delayMs={}", siteCode, requestType,
			target, requestDelay.toMillis());
		requestSleeper.sleep(requestDelay);
	}

	private void validateProduct(CrawledProductDetail detail, CrawledProductSummary summary) {
		if (firstText(detail.name(), summary.name()) == null) {
			throw new IllegalStateException("blank product name");
		}
	}

	private String successMessage(CrawlCounts counts) {
		if (counts.failureReasons.isEmpty()) {
			return "Crawl run completed.";
		}
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
