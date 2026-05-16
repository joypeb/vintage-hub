package com.joypeb.vintagehub.crawl.application;

public record CrawlRunStatusResult(
		Long runId,
		String siteCode,
		String status,
		int foundCount,
		int createdCount,
		int updatedCount,
		int failedCount,
		String message
) {
}
