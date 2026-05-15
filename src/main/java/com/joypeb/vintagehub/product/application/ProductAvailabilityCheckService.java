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
import java.util.List;

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
		ProductEntity product = productRepository.findById(productId)
			.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
		return checkProducts(List.of(product));
	}

	@Transactional
	public ProductAvailabilityCheckResult checkDueProducts() {
		return checkDueProducts(properties.batchSize());
	}

	@Transactional
	public ProductAvailabilityCheckResult checkDueProducts(int batchSize) {
		int resolvedBatchSize = Math.max(batchSize, 1);
		List<ProductEntity> products = productRepository.findDueForAvailabilityCheck(Instant.now(clock),
			PageRequest.of(0, resolvedBatchSize));
		return checkProducts(products);
	}

	private ProductAvailabilityCheckResult checkProducts(List<ProductEntity> products) {
		AvailabilityCheckCounts counts = new AvailabilityCheckCounts();
		for (ProductEntity product : products) {
			delayBeforeRequest(product);
			checkOneProduct(product, counts);
		}
		return counts.toResult();
	}

	private void checkOneProduct(ProductEntity product, AvailabilityCheckCounts counts) {
		Instant checkedAt = Instant.now(clock);
		try {
			SiteCrawler crawler = crawlerRegistry.requireBySiteCode(product.site().code());
			ProductAvailability availability = crawler.checkAvailability(
				new CrawlTargetSite(product.site().code(), product.site().baseUrl()),
				new CrawledProductRef(product.sourceProductId(), URI.create(product.detailUrl()))
			);
			product.markAvailabilityCheckSucceeded(availability, checkedAt, nextCheckAt(availability, checkedAt));
			productRepository.save(product);
			counts.record(availability);
			log.info("Product availability checked: productId={} siteCode={} sourceProductId={} availability={}",
				product.id(), product.site().code(), product.sourceProductId(), availability);
		}
		catch (RuntimeException exception) {
			product.markAvailabilityCheckFailed(checkedAt, nextCheckAt(ProductAvailability.CHECK_FAILED, checkedAt));
			productRepository.save(product);
			counts.recordFailure();
			log.warn("Product availability check failed: productId={} siteCode={} sourceProductId={} reason={}",
				product.id(), product.site().code(), product.sourceProductId(), failureMessage(exception), exception);
		}
	}

	private Instant nextCheckAt(ProductAvailability availability, Instant checkedAt) {
		return checkedAt.plus(ttlFor(availability));
	}

	private Duration ttlFor(ProductAvailability availability) {
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
			return;
		}
		log.debug("Product availability request delay: productId={} delayMs={}", product.id(),
			requestDelay.toMillis());
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
			checkedCount++;
			switch (availability) {
				case AVAILABLE -> availableCount++;
				case SOLD_OUT -> soldOutCount++;
				case UNKNOWN -> unknownCount++;
				case CHECK_FAILED -> failedCount++;
			}
		}

		private void recordFailure() {
			checkedCount++;
			failedCount++;
		}

		private ProductAvailabilityCheckResult toResult() {
			return new ProductAvailabilityCheckResult(checkedCount, availableCount, soldOutCount, unknownCount,
				failedCount);
		}
	}
}
