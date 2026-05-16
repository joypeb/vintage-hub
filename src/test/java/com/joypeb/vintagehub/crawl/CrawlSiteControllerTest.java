package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.crawl.application.CrawlSiteQueryService;
import com.joypeb.vintagehub.crawl.application.CrawlSiteResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlSiteController.class)
class CrawlSiteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrawlSiteQueryService crawlSiteQueryService;

	@Test
	void getCrawlableSitesReturnsRegisteredCrawlerSites() throws Exception {
		when(crawlSiteQueryService.getCrawlableSites())
			.thenReturn(List.of(new CrawlSiteResult("rocketsalad", "로켓샐러드",
				URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60, "ACTIVE",
				Instant.parse("2026-05-16T08:00:00Z"))));

		mockMvc.perform(get("/api/admin/crawl-sites"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data[0].siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data[0].displayName").value("로켓샐러드"))
			.andExpect(jsonPath("$.data[0].baseUrl").value("https://www.rocketsalad.co.kr"))
			.andExpect(jsonPath("$.data[0].platform").value("MakeShop"))
			.andExpect(jsonPath("$.data[0].crawlIntervalMinutes").value(60))
			.andExpect(jsonPath("$.data[0].crawlerStatus").value("ACTIVE"))
			.andExpect(jsonPath("$.data[0].lastCrawledAt").value("2026-05-16T08:00:00Z"));
	}
}
