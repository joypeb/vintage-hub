package com.joypeb.vintagehub.crawl.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CrawlRunScheduler {

	private static final Logger log = LoggerFactory.getLogger(CrawlRunScheduler.class);

	private final CrawlRunRequestService service;
	private final CrawlRunScheduleProperties properties;

	CrawlRunScheduler(CrawlRunRequestService service, CrawlRunScheduleProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Scheduled(fixedRateString = "${vintage-hub.crawl.schedule.fixed-rate:1h}")
	void runScheduledCrawls() {
		if (!properties.enabled()) {
			log.atDebug()
				.addKeyValue("event", "crawl.schedule.skipped")
				.addKeyValue("reason", "disabled")
				.log("crawl.schedule.skipped");
			return;
		}
		for (String siteCode : properties.siteCodes()) {
			runSite(siteCode);
		}
	}

	private void runSite(String siteCode) {
		try {
			log.atInfo()
				.addKeyValue("event", "crawl.schedule.site.started")
				.addKeyValue("siteCode", siteCode)
				.log("crawl.schedule.site.started");
			CrawlRunStatusResult result = service.requestScheduledRun(siteCode);
			log.atInfo()
				.addKeyValue("event", "crawl.schedule.site.completed")
				.addKeyValue("siteCode", siteCode)
				.addKeyValue("runId", result.runId())
				.addKeyValue("createdCount", result.createdCount())
				.addKeyValue("updatedCount", result.updatedCount())
				.addKeyValue("failedCount", result.failedCount())
				.log("crawl.schedule.site.completed");
		}
		catch (RuntimeException exception) {
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "crawl.schedule.site.failed")
				.addKeyValue("siteCode", siteCode)
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.schedule.site.failed");
		}
	}
}
