package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.crawl.persistence.CrawlTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlRunRequestServiceTest {

	@Test
	void requestManualRunStartsRunAndExecutesCrawlerInBackgroundExecutor() {
		CrawlRunLifecycleService lifecycleService = mock(CrawlRunLifecycleService.class);
		CrawlRunService crawlRunService = mock(CrawlRunService.class);
		CrawlerRegistry crawlerRegistry = mock(CrawlerRegistry.class);
		TaskExecutor directExecutor = Runnable::run;
		CrawlRunRequestService service = new CrawlRunRequestService(lifecycleService, crawlRunService, crawlerRegistry,
			directExecutor);
		when(crawlerRegistry.requireBySiteCode("rocketsalad")).thenReturn(mock(SiteCrawler.class));
		CrawlRunStatusResult startedRun = new CrawlRunStatusResult(42L, "rocketsalad", "RUNNING", 0, 0, 0, 0,
			"Crawl run started.");
		when(lifecycleService.startRun("rocketsalad", CrawlTriggerType.MANUAL)).thenReturn(startedRun);

		CrawlRunStatusResult result = service.requestManualRun("rocketsalad");

		assertThat(result).isEqualTo(startedRun);
		verify(crawlRunService).executeRun(42L, "rocketsalad");
	}
}
