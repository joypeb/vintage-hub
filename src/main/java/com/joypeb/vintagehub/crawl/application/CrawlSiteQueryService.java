package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CrawlSiteQueryService {

	private final CrawlSiteRepository siteRepository;
	private final CrawlerRegistry crawlerRegistry;

	CrawlSiteQueryService(CrawlSiteRepository siteRepository, CrawlerRegistry crawlerRegistry) {
		this.siteRepository = siteRepository;
		this.crawlerRegistry = crawlerRegistry;
	}

	@Transactional(readOnly = true)
	public List<CrawlSiteResult> getCrawlableSites() {
		return siteRepository.findAll()
			.stream()
			.filter(site -> crawlerRegistry.supports(site.code()))
			.sorted(Comparator.comparing(CrawlSiteEntity::code))
			.map(this::toResult)
			.toList();
	}

	private CrawlSiteResult toResult(CrawlSiteEntity site) {
		return new CrawlSiteResult(site.code(), site.displayName(), site.baseUrl(), site.platform(),
			site.crawlIntervalMinutes(), site.crawlerStatus().name(), site.lastCrawledAt());
	}
}
