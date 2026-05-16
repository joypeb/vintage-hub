package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunStatus;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlTriggerType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class CrawlRunLifecycleService {

	private static final List<CrawlRunStatus> ACTIVE_STATUSES = List.of(CrawlRunStatus.PENDING, CrawlRunStatus.RUNNING);

	private final CrawlSiteRepository siteRepository;
	private final CrawlRunRepository runRepository;
	private final CrawlRunEventPublisher eventPublisher;

	CrawlRunLifecycleService(CrawlSiteRepository siteRepository, CrawlRunRepository runRepository,
			CrawlRunEventPublisher eventPublisher) {
		this.siteRepository = siteRepository;
		this.runRepository = runRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult startRun(String siteCode, CrawlTriggerType triggerType) {
		if (runRepository.existsBySite_CodeAndStatusIn(siteCode, ACTIVE_STATUSES)) {
			throw new CrawlRunAlreadyActiveException(siteCode);
		}
		CrawlSiteEntity site = siteRepository.findByCode(siteCode)
			.orElseThrow(() -> new IllegalArgumentException("Crawl site not found: " + siteCode));
		CrawlRunEntity run = triggerType == CrawlTriggerType.MANUAL
				? CrawlRunEntity.manual(site)
				: CrawlRunEntity.scheduled(site);
		run.markRunning();
		CrawlRunStatusResult result = toResult(runRepository.save(run));
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markProgress(Long runId, int foundCount, int createdCount, int updatedCount,
			int failedCount, String message) {
		CrawlRunEntity run = findRun(runId);
		run.markProgress(foundCount, createdCount, updatedCount, failedCount, message);
		CrawlRunStatusResult result = toResult(run);
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markSucceeded(Long runId, int foundCount, int createdCount, int updatedCount,
			int failedCount, String message) {
		CrawlRunEntity run = findRun(runId);
		run.markSucceeded(foundCount, createdCount, updatedCount, failedCount, message);
		CrawlRunStatusResult result = toResult(run);
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markFailed(Long runId, String message) {
		CrawlRunEntity run = findRun(runId);
		run.markFailed(message);
		CrawlRunStatusResult result = toResult(run);
		publishAfterCommit(result);
		return result;
	}

	private CrawlRunEntity findRun(Long runId) {
		return runRepository.findById(runId)
			.orElseThrow(() -> new ResourceNotFoundException("크롤링 실행을 찾을 수 없습니다. runId=" + runId));
	}

	private CrawlRunStatusResult toResult(CrawlRunEntity run) {
		return new CrawlRunStatusResult(run.id(), run.site().code(), run.status().name(), run.foundCount(),
			run.createdCount(), run.updatedCount(), run.failedCount(), run.message());
	}

	private void publishAfterCommit(CrawlRunStatusResult result) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			eventPublisher.publish(result);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				eventPublisher.publish(result);
			}
		});
	}
}
