package com.joypeb.vintagehub.crawl.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CrawlRunSchedulerTest {

	@Test
	void runScheduledCrawlsRequestsConfiguredSites() {
		CrawlRunRequestService service = mock(CrawlRunRequestService.class);
		CrawlRunScheduler scheduler = new CrawlRunScheduler(service,
			new CrawlRunScheduleProperties(true, Duration.ofHours(1), List.of("rocketsalad")));

		scheduler.runScheduledCrawls();

		verify(service).requestScheduledRun("rocketsalad");
	}

	@Test
	void runScheduledCrawlsSkipsWhenDisabled() {
		CrawlRunRequestService service = mock(CrawlRunRequestService.class);
		CrawlRunScheduler scheduler = new CrawlRunScheduler(service,
			new CrawlRunScheduleProperties(false, Duration.ofHours(1), List.of("rocketsalad")));

		scheduler.runScheduledCrawls();

		verify(service, never()).requestScheduledRun("rocketsalad");
	}
}
