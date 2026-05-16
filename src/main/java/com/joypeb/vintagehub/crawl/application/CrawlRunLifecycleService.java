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
			// 같은 사이트에 대해 실행 중인 크롤링이 있으면 중복 실행을 막는다.
			throw new CrawlRunAlreadyActiveException(siteCode);
		}
		CrawlSiteEntity site = siteRepository.findByCode(siteCode)
			.orElseThrow(() -> new IllegalArgumentException("Crawl site not found: " + siteCode));
		CrawlRunEntity run = triggerType == CrawlTriggerType.MANUAL
				? CrawlRunEntity.manual(site)
				: CrawlRunEntity.scheduled(site);
		// 실행 요청이 생성되면 작업 큐가 바로 소비할 수 있도록 RUNNING 상태로 전환한다.
		run.markRunning();
		CrawlRunStatusResult result = toResult(runRepository.save(run));
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markProgress(Long runId, int foundCount, int createdCount, int updatedCount,
			int failedCount, String message) {
		CrawlRunEntity run = findRun(runId);
		// 상품 단위 진행 상황을 별도 트랜잭션에 저장해 긴 크롤링 중에도 상태 조회가 가능하게 한다.
		run.markProgress(foundCount, createdCount, updatedCount, failedCount, message);
		CrawlRunStatusResult result = toResult(run);
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markSucceeded(Long runId, int foundCount, int createdCount, int updatedCount,
			int failedCount, String message) {
		CrawlRunEntity run = findRun(runId);
		// 최종 성공 상태와 누적 카운트를 한 번에 반영한다.
		run.markSucceeded(foundCount, createdCount, updatedCount, failedCount, message);
		CrawlRunStatusResult result = toResult(run);
		publishAfterCommit(result);
		return result;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CrawlRunStatusResult markFailed(Long runId, String message) {
		CrawlRunEntity run = findRun(runId);
		// 실행 중 예외가 발생하면 실패 메시지를 남겨 관리자 화면에서 원인을 확인하게 한다.
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
			// 트랜잭션 밖에서 호출된 경우에는 지연 없이 바로 이벤트를 발행한다.
			eventPublisher.publish(result);
			return;
		}
		// 커밋된 상태만 SSE 구독자에게 전달해 롤백된 진행 상황이 보이지 않게 한다.
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				eventPublisher.publish(result);
			}
		});
	}
}
