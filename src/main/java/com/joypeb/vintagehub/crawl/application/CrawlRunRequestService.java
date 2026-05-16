package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.persistence.CrawlTriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class CrawlRunRequestService {

	private static final Logger log = LoggerFactory.getLogger(CrawlRunRequestService.class);

	private final CrawlRunLifecycleService lifecycleService;
	private final CrawlRunService crawlRunService;
	private final CrawlerRegistry crawlerRegistry;
	private final TaskExecutor crawlRunTaskExecutor;

	CrawlRunRequestService(CrawlRunLifecycleService lifecycleService, CrawlRunService crawlRunService,
			CrawlerRegistry crawlerRegistry, @Qualifier("crawlRunTaskExecutor") TaskExecutor crawlRunTaskExecutor) {
		this.lifecycleService = lifecycleService;
		this.crawlRunService = crawlRunService;
		this.crawlerRegistry = crawlerRegistry;
		this.crawlRunTaskExecutor = crawlRunTaskExecutor;
	}

	public CrawlRunStatusResult requestManualRun(String siteCode) {
		return requestRun(siteCode, CrawlTriggerType.MANUAL);
	}

	public CrawlRunStatusResult requestScheduledRun(String siteCode) {
		return requestRun(siteCode, CrawlTriggerType.SCHEDULED);
	}

	private CrawlRunStatusResult requestRun(String siteCode, CrawlTriggerType triggerType) {
		crawlerRegistry.requireBySiteCode(siteCode);
		CrawlRunStatusResult startedRun = lifecycleService.startRun(siteCode, triggerType);
		crawlRunTaskExecutor.execute(() -> executeRun(startedRun));
		return startedRun;
	}

	private void executeRun(CrawlRunStatusResult startedRun) {
		try {
			crawlRunService.executeRun(startedRun.runId(), startedRun.siteCode());
		}
		catch (RuntimeException exception) {
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "crawl.run.background.failed")
				.addKeyValue("runId", startedRun.runId())
				.addKeyValue("siteCode", startedRun.siteCode())
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.run.background.failed");
		}
	}
}
