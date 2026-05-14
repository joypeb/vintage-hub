package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.crawl.application.CrawlRunResult;
import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlRunController.class)
class CrawlRunControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrawlRunService crawlRunService;

	@Test
	void requestCrawlRunAcceptsSiteCode() throws Exception {
		when(crawlRunService.requestManualRun("rocketsalad"))
			.thenReturn(new CrawlRunResult("rocketsalad", "SUCCEEDED", 2, 1, 1, 0, "Crawl run completed."));

		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.foundCount").value(2))
			.andExpect(jsonPath("$.createdCount").value(1))
			.andExpect(jsonPath("$.updatedCount").value(1))
			.andExpect(jsonPath("$.failedCount").value(0))
			.andExpect(jsonPath("$.message").value("Crawl run completed."));
	}
}
