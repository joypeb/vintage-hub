package com.joypeb.vintagehub.crawl.domain;

import java.net.URI;

public record CrawlTargetSite(
	String code,
	URI baseUrl
) {
}
