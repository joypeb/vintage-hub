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
			// 설정으로 스케줄을 끈 환경에서는 등록된 사이트가 있어도 실행하지 않는다.
			log.atDebug()
				.addKeyValue("event", "crawl.schedule.skipped")
				.addKeyValue("reason", "disabled")
				.log("crawl.schedule.skipped");
			return;
		}
		for (String siteCode : properties.siteCodes()) {
			// 사이트별 실패가 전체 스케줄 루프를 중단하지 않도록 개별 실행한다.
			runSite(siteCode);
		}
	}

	private void runSite(String siteCode) {
		try {
			// 스케줄러는 실행 요청까지만 담당하고 실제 크롤링은 비동기 서비스가 처리한다.
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
			// 한 사이트 요청 실패는 로그로 남기고 다음 사이트 스케줄을 계속 진행한다.
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "crawl.schedule.site.failed")
				.addKeyValue("siteCode", siteCode)
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.schedule.site.failed");
		}
	}
}
