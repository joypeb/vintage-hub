package com.joypeb.vintagehub.crawl.domain;

import java.util.List;

public record CrawlListResult(
	List<CrawledProductSummary> products,
	CrawlCursor nextCursor
) {
}
