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
			log.atDebug()
				.addKeyValue("event", "product.availability.batch.skipped")
				.addKeyValue("reason", "disabled")
				.log("product.availability.batch.skipped");
			return;
		}
		log.atInfo()
			.addKeyValue("event", "product.availability.batch.started")
			.log("product.availability.batch.started");
		ProductAvailabilityCheckResult result = service.checkDueProducts();
		log.atInfo()
			.addKeyValue("event", "product.availability.batch.completed")
			.addKeyValue("checkedCount", result.checkedCount())
			.addKeyValue("availableCount", result.availableCount())
			.addKeyValue("soldOutCount", result.soldOutCount())
			.addKeyValue("unknownCount", result.unknownCount())
			.addKeyValue("failedCount", result.failedCount())
			.log("product.availability.batch.completed");
	}
}
