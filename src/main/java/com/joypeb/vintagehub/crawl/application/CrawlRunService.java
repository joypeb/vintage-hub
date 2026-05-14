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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CrawlRunService {

	private static final int MAX_PAGES_PER_INITIAL_CURSOR = 3;

	private final CrawlSiteRepository siteRepository;
	private final CrawlRunRepository runRepository;
	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;
	private final CrawlerRegistry crawlerRegistry;

	public CrawlRunService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			ProductRepository productRepository, ProductMeasurementRepository measurementRepository,
			CrawlerRegistry crawlerRegistry) {
		this.siteRepository = siteRepository;
		this.runRepository = runRepository;
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
		this.crawlerRegistry = crawlerRegistry;
	}

	@Transactional
	public CrawlRunResult requestManualRun(String siteCode) {
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
			return new CrawlRunResult(site.code(), run.status().name(), counts.foundCount, counts.createdCount,
				counts.updatedCount, counts.failedCount, run.message());
		}
		catch (RuntimeException exception) {
			run.markFailed(exception.getMessage());
			throw exception;
		}
	}

	private CrawlCounts saveProducts(CrawlSiteEntity site, SiteCrawler crawler) {
		CrawlCounts counts = new CrawlCounts();
		CrawlTargetSite targetSite = new CrawlTargetSite(site.code(), site.baseUrl());
		List<CrawlCursor> initialCursors = crawler.initialCursors();

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
			CrawlListResult listResult = crawler.fetchList(targetSite, cursor);
			boolean stopPaging = savePageProducts(site, crawler, targetSite, listResult.products(), counts);
			if (stopPaging || listResult.nextCursor() == null || listResult.products().isEmpty()) {
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
			if (productRepository.findBySiteAndSourceProductId(site, summary.ref().sourceProductId()).isPresent()) {
				return true;
			}
			try {
				CrawledProductDetail detail = crawler.fetchDetail(targetSite, summary.ref());
				ProductEntity product = ProductEntity.create(site, detail.ref().sourceProductId());
				product.updateFrom(detail, summary, collectedAt);
				ProductEntity savedProduct = productRepository.save(product);
				replaceMeasurements(savedProduct, detail);
				counts.createdCount++;
			}
			catch (RuntimeException exception) {
				counts.failedCount++;
				counts.failureReasons.add(summary.ref().sourceProductId() + ": " + failureMessage(exception));
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

	private static class CrawlCounts {
		private int foundCount;
		private int createdCount;
		private int updatedCount;
		private int failedCount;
		private final List<String> failureReasons = new ArrayList<>();
	}
}
