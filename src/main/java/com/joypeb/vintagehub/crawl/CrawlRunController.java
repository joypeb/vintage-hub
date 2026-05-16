package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.application.CrawlRunEventPublisher;
import com.joypeb.vintagehub.crawl.application.CrawlRunQueryService;
import com.joypeb.vintagehub.crawl.application.CrawlRunRequestService;
import com.joypeb.vintagehub.crawl.application.CrawlRunStatusResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
class CrawlRunController {

	private final CrawlRunRequestService crawlRunRequestService;
	private final CrawlRunQueryService crawlRunQueryService;
	private final CrawlRunEventPublisher crawlRunEventPublisher;

	CrawlRunController(CrawlRunRequestService crawlRunRequestService, CrawlRunQueryService crawlRunQueryService,
			CrawlRunEventPublisher crawlRunEventPublisher) {
		this.crawlRunRequestService = crawlRunRequestService;
		this.crawlRunQueryService = crawlRunQueryService;
		this.crawlRunEventPublisher = crawlRunEventPublisher;
	}

	@PostMapping("/api/admin/crawl-sites/{siteCode}/crawl-runs")
	ResponseEntity<ApiResponse<CrawlRunResponse>> requestCrawlRun(@PathVariable String siteCode) {
		CrawlRunStatusResult result = crawlRunRequestService.requestManualRun(siteCode);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(CrawlRunResponse.from(result)));
	}

	@GetMapping("/api/admin/crawl-runs/{runId}")
	ApiResponse<CrawlRunResponse> getCrawlRun(@PathVariable Long runId) {
		return ApiResponse.success(CrawlRunResponse.from(crawlRunQueryService.getRun(runId)));
	}

	@GetMapping("/api/admin/crawl-runs/active")
	ApiResponse<List<CrawlRunResponse>> getActiveCrawlRuns() {
		return ApiResponse.success(crawlRunQueryService.getActiveRuns()
			.stream()
			.map(CrawlRunResponse::from)
			.toList());
	}

	@GetMapping(path = "/api/admin/crawl-runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter subscribeCrawlRunEvents(@PathVariable Long runId) {
		return crawlRunEventPublisher.subscribe(runId);
	}

	record CrawlRunResponse(Long runId, String siteCode, String status, int foundCount, int createdCount,
			int updatedCount, int failedCount, String message) {

		private static CrawlRunResponse from(CrawlRunStatusResult result) {
			return new CrawlRunResponse(result.runId(), result.siteCode(), result.status(), result.foundCount(),
				result.createdCount(), result.updatedCount(), result.failedCount(), result.message());
		}
	}
}
