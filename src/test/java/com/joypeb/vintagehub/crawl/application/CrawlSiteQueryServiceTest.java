package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlSiteQueryServiceTest {

	@Test
	void getCrawlableSitesReturnsOnlySitesWithRegisteredCrawler() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlSiteQueryService service = new CrawlSiteQueryService(siteRepository,
			new CrawlerRegistry(List.of(new StubSiteCrawler("rocketsalad"))));
		CrawlSiteEntity rocketSalad = CrawlSiteEntity.create("rocketsalad", "로켓샐러드",
			URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		CrawlSiteEntity unsupported = CrawlSiteEntity.create("unsupported", "미지원",
			URI.create("https://unsupported.example"), "Unknown", 60);
		when(siteRepository.findAll()).thenReturn(List.of(unsupported, rocketSalad));

		List<CrawlSiteResult> results = service.getCrawlableSites();

		assertThat(results).extracting(CrawlSiteResult::siteCode).containsExactly("rocketsalad");
		assertThat(results.getFirst().displayName()).isEqualTo("로켓샐러드");
		assertThat(results.getFirst().baseUrl()).isEqualTo(URI.create("https://www.rocketsalad.co.kr"));
		assertThat(results.getFirst().platform()).isEqualTo("MakeShop");
		assertThat(results.getFirst().crawlerStatus()).isEqualTo("ACTIVE");
	}

	private record StubSiteCrawler(String siteCode) implements SiteCrawler {

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, com.joypeb.vintagehub.crawl.domain.CrawlCursor cursor) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			throw new UnsupportedOperationException();
		}
	}
}
