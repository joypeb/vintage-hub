package com.joypeb.vintagehub.crawl.application;

import java.net.URI;
import java.time.Instant;

public record CrawlSiteResult(
		String siteCode,
		String displayName,
		URI baseUrl,
		String platform,
		int crawlIntervalMinutes,
		String crawlerStatus,
		Instant lastCrawledAt
) {
}
