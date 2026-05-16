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
		// 실행 건을 만들기 전에 해당 사이트를 처리할 크롤러가 등록되어 있는지 검증한다.
		crawlerRegistry.requireBySiteCode(siteCode);
		CrawlRunStatusResult startedRun = lifecycleService.startRun(siteCode, triggerType);
		// API 요청 스레드는 시작 상태만 반환하고 실제 크롤링은 전용 executor에서 수행한다.
		crawlRunTaskExecutor.execute(() -> executeRun(startedRun));
		return startedRun;
	}

	private void executeRun(CrawlRunStatusResult startedRun) {
		try {
			// 시작된 실행 ID를 기준으로 진행률과 최종 상태를 갱신한다.
			crawlRunService.executeRun(startedRun.runId(), startedRun.siteCode());
		}
		catch (RuntimeException exception) {
			// executeRun 내부에서 실패 상태를 저장한 뒤에도 작업 스레드 예외는 로그로 남긴다.
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
