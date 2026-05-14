package com.joypeb.vintagehub.crawl.domain;

import java.util.List;

public interface SiteCrawler {

	String siteCode();

	default List<CrawlCursor> initialCursors() {
		return List.of();
	}

	CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor);

	CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef);

	ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef);
}
