package com.joypeb.vintagehub.crawl.domain;

import java.net.URI;

public record CrawledProductRef(
	String sourceProductId,
	URI detailUrl
) {
}
