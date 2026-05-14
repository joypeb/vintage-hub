package com.joypeb.vintagehub.crawl.domain;

public interface SiteCrawler {

	String siteCode();

	CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor);

	CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef);

	ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef);
}
