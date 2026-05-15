package com.joypeb.vintagehub.crawl.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "vintage-hub.crawl.schedule")
public record CrawlRunScheduleProperties(
	boolean enabled,
	Duration fixedRate,
	List<String> siteCodes
) {
	public CrawlRunScheduleProperties {
		if (fixedRate == null) {
			fixedRate = Duration.ofHours(1);
		}
		if (siteCodes == null || siteCodes.isEmpty()) {
			siteCodes = List.of("rocketsalad");
		}
	}
}
