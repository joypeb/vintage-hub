package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CrawlerRegistry {

	private final Map<String, SiteCrawler> crawlers;

	public CrawlerRegistry(List<SiteCrawler> crawlers) {
		this.crawlers = mapBySiteCode(crawlers);
	}

	public SiteCrawler requireBySiteCode(String siteCode) {
		SiteCrawler crawler = crawlers.get(siteCode);
		if (crawler == null) {
			throw new IllegalArgumentException("Unsupported crawler site code: " + siteCode);
		}
		return crawler;
	}

	private Map<String, SiteCrawler> mapBySiteCode(List<SiteCrawler> crawlers) {
		Map<String, SiteCrawler> mapped = new HashMap<>();
		for (SiteCrawler crawler : crawlers) {
			SiteCrawler previous = mapped.putIfAbsent(crawler.siteCode(), crawler);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicated crawler site code: " + crawler.siteCode());
			}
		}
		return Map.copyOf(mapped);
	}
}
