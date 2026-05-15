package com.joypeb.vintagehub.product.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vintage-hub.product.availability-check")
public record ProductAvailabilityCheckProperties(
	boolean enabled,
	Duration fixedRate,
	int batchSize,
	Duration requestDelay,
	Duration availableTtl,
	Duration soldOutTtl,
	Duration unknownTtl,
	Duration checkFailedTtl
) {
	public ProductAvailabilityCheckProperties {
		if (fixedRate == null) {
			fixedRate = Duration.ofMinutes(10);
		}
		if (batchSize < 1) {
			batchSize = 20;
		}
		if (requestDelay == null) {
			requestDelay = Duration.ofSeconds(1);
		}
		if (availableTtl == null) {
			availableTtl = Duration.ofHours(6);
		}
		if (soldOutTtl == null) {
			soldOutTtl = Duration.ofDays(7);
		}
		if (unknownTtl == null) {
			unknownTtl = Duration.ofHours(1);
		}
		if (checkFailedTtl == null) {
			checkFailedTtl = Duration.ofHours(2);
		}
	}
}
