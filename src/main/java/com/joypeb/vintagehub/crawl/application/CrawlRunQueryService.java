package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CrawlRunQueryService {

	private static final List<CrawlRunStatus> ACTIVE_STATUSES = List.of(CrawlRunStatus.PENDING, CrawlRunStatus.RUNNING);

	private final CrawlRunRepository runRepository;

	CrawlRunQueryService(CrawlRunRepository runRepository) {
		this.runRepository = runRepository;
	}

	@Transactional(readOnly = true)
	public CrawlRunStatusResult getRun(Long runId) {
		return runRepository.findById(runId)
			.map(this::toResult)
			.orElseThrow(() -> new ResourceNotFoundException("크롤링 실행을 찾을 수 없습니다. runId=" + runId));
	}

	@Transactional(readOnly = true)
	public List<CrawlRunStatusResult> getActiveRuns() {
		return runRepository.findByStatusInOrderByStartedAtDesc(ACTIVE_STATUSES)
			.stream()
			.map(this::toResult)
			.toList();
	}

	private CrawlRunStatusResult toResult(CrawlRunEntity run) {
		return new CrawlRunStatusResult(run.id(), run.site().code(), run.status().name(), run.foundCount(),
			run.createdCount(), run.updatedCount(), run.failedCount(), run.message());
	}
}
