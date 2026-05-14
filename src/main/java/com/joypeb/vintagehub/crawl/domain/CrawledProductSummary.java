package com.joypeb.vintagehub.crawl.domain;

import java.net.URI;

public record CrawledProductSummary(
	CrawledProductRef ref,
	String name,
	URI thumbnailImageUrl
) {
}
