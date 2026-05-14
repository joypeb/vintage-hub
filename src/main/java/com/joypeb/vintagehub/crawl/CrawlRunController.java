package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.crawl.application.CrawlRunResult;
import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/crawl-sites")
class CrawlRunController {

	private final CrawlRunService crawlRunService;

	CrawlRunController(CrawlRunService crawlRunService) {
		this.crawlRunService = crawlRunService;
	}

	@PostMapping("/{siteCode}/crawl-runs")
	ResponseEntity<CrawlRunResponse> requestCrawlRun(@PathVariable String siteCode) {
		CrawlRunResult result = crawlRunService.requestManualRun(siteCode);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(CrawlRunResponse.from(result));
	}

	record CrawlRunResponse(String siteCode, String status, int foundCount, int createdCount, int updatedCount,
			int failedCount, String message) {

		private static CrawlRunResponse from(CrawlRunResult result) {
			return new CrawlRunResponse(result.siteCode(), result.status(), result.foundCount(), result.createdCount(),
				result.updatedCount(), result.failedCount(), result.message());
		}
	}
}
