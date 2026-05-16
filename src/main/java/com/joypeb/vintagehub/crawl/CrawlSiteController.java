package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.application.CrawlSiteQueryService;
import com.joypeb.vintagehub.crawl.application.CrawlSiteResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
class CrawlSiteController {

	private final CrawlSiteQueryService crawlSiteQueryService;

	CrawlSiteController(CrawlSiteQueryService crawlSiteQueryService) {
		this.crawlSiteQueryService = crawlSiteQueryService;
	}

	@GetMapping("/api/admin/crawl-sites")
	ApiResponse<List<CrawlSiteResponse>> getCrawlableSites() {
		return ApiResponse.success(crawlSiteQueryService.getCrawlableSites()
			.stream()
			.map(CrawlSiteResponse::from)
			.toList());
	}

	record CrawlSiteResponse(String siteCode, String displayName, URI baseUrl, String platform,
			int crawlIntervalMinutes, String crawlerStatus, Instant lastCrawledAt) {

		private static CrawlSiteResponse from(CrawlSiteResult result) {
			return new CrawlSiteResponse(result.siteCode(), result.displayName(), result.baseUrl(), result.platform(),
				result.crawlIntervalMinutes(), result.crawlerStatus(), result.lastCrawledAt());
		}
	}
}
