package com.joypeb.vintagehub.crawl;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/crawl-sites")
class CrawlRunController {

	@PostMapping("/{siteCode}/crawl-runs")
	ResponseEntity<CrawlRunResponse> requestCrawlRun(@PathVariable String siteCode) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(new CrawlRunResponse(siteCode, "ACCEPTED", "Crawl request accepted."));
	}

	record CrawlRunResponse(String siteCode, String status, String message) {
	}
}
