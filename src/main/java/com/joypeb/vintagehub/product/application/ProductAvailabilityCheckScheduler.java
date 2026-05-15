package com.joypeb.vintagehub.product.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProductAvailabilityCheckScheduler {

	private static final Logger log = LoggerFactory.getLogger(ProductAvailabilityCheckScheduler.class);

	private final ProductAvailabilityCheckService service;
	private final ProductAvailabilityCheckProperties properties;

	ProductAvailabilityCheckScheduler(ProductAvailabilityCheckService service,
			ProductAvailabilityCheckProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Scheduled(fixedRateString = "${vintage-hub.product.availability-check.fixed-rate:10m}")
	void checkDueProducts() {
		if (!properties.enabled()) {
			return;
		}
		ProductAvailabilityCheckResult result = service.checkDueProducts();
		log.info("Product availability batch finished: checked={} available={} soldOut={} unknown={} failed={}",
			result.checkedCount(), result.availableCount(), result.soldOutCount(), result.unknownCount(),
			result.failedCount());
	}
}
