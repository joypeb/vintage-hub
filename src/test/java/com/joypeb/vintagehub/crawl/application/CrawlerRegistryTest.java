package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.domain.SiteCrawler;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrawlerRegistryTest {

	@Test
	void findsCrawlerBySiteCode() {
		SiteCrawler rocketSaladCrawler = new StubSiteCrawler("rocketsalad");
		CrawlerRegistry registry = new CrawlerRegistry(List.of(rocketSaladCrawler));

		SiteCrawler found = registry.requireBySiteCode("rocketsalad");

		assertThat(found).isSameAs(rocketSaladCrawler);
	}

	@Test
	void throwsWhenSiteCodeIsNotSupported() {
		CrawlerRegistry registry = new CrawlerRegistry(List.of(new StubSiteCrawler("rocketsalad")));

		assertThatThrownBy(() -> registry.requireBySiteCode("unknown"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unsupported crawler site code: unknown");
	}

	@Test
	void rejectsDuplicatedSiteCode() {
		SiteCrawler first = new StubSiteCrawler("rocketsalad");
		SiteCrawler second = new StubSiteCrawler("rocketsalad");

		assertThatThrownBy(() -> new CrawlerRegistry(List.of(first, second)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Duplicated crawler site code: rocketsalad");
	}

	private record StubSiteCrawler(String siteCode) implements SiteCrawler {

		@Override
		public CrawlListResult fetchList(CrawlTargetSite site, CrawlCursor cursor) {
			return new CrawlListResult(List.of(), null);
		}

		@Override
		public CrawledProductDetail fetchDetail(CrawlTargetSite site, CrawledProductRef productRef) {
			return new CrawledProductDetail(productRef, null, null, null, ProductAvailability.UNKNOWN, null, null, null, Map.of());
		}

		@Override
		public ProductAvailability checkAvailability(CrawlTargetSite site, CrawledProductRef productRef) {
			return ProductAvailability.UNKNOWN;
		}
	}
}
