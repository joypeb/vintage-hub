package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
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
			CrawlListResult listResult = crawler.fetchList(new CrawlTargetSite(site.code(), site.baseUrl()), null);
			CrawlCounts counts = saveProducts(site, crawler, listResult.products());
			run.markSucceeded(counts.foundCount(), counts.createdCount(), counts.updatedCount(), counts.failedCount(),
				"Crawl run completed.");
			site.markCrawled(counts.createdCount() + counts.updatedCount() > 0);
			return new CrawlRunResult(site.code(), run.status().name(), counts.foundCount(), counts.createdCount(),
				counts.updatedCount(), counts.failedCount(), run.message());
		}
		catch (RuntimeException exception) {
			run.markFailed(exception.getMessage());
			throw exception;
		}
	}

	private CrawlCounts saveProducts(CrawlSiteEntity site, SiteCrawler crawler, List<CrawledProductSummary> summaries) {
		int createdCount = 0;
		int updatedCount = 0;
		int failedCount = 0;
		Instant collectedAt = Instant.now();
		CrawlTargetSite targetSite = new CrawlTargetSite(site.code(), site.baseUrl());

		for (CrawledProductSummary summary : summaries) {
			try {
				CrawledProductDetail detail = crawler.fetchDetail(targetSite, summary.ref());
				var existingProduct = productRepository.findBySiteAndSourceProductId(site, detail.ref().sourceProductId());
				boolean exists = existingProduct.isPresent();
				ProductEntity product = existingProduct.orElseGet(() -> ProductEntity.create(site, detail.ref().sourceProductId()));
				product.updateFrom(detail, summary, collectedAt);
				ProductEntity savedProduct = productRepository.save(product);
				replaceMeasurements(savedProduct, detail);
				if (exists) {
					updatedCount++;
				}
				else {
					createdCount++;
				}
			}
			catch (RuntimeException exception) {
				failedCount++;
			}
		}
		return new CrawlCounts(summaries.size(), createdCount, updatedCount, failedCount);
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

	private record CrawlCounts(int foundCount, int createdCount, int updatedCount, int failedCount) {
	}
}
