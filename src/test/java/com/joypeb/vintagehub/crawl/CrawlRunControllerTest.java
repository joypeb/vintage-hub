package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.crawl.application.CrawlRunResult;
import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
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
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.data.foundCount").value(2))
			.andExpect(jsonPath("$.data.createdCount").value(1))
			.andExpect(jsonPath("$.data.updatedCount").value(1))
			.andExpect(jsonPath("$.data.failedCount").value(0))
			.andExpect(jsonPath("$.data.message").value("Crawl run completed."))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void requestCrawlRunReturnsCommonErrorResponseForInvalidSiteCode() throws Exception {
		doThrow(new IllegalArgumentException("Unsupported crawler site code: unknown"))
			.when(crawlRunService)
			.requestManualRun("unknown");

		mockMvc.perform(post("/api/admin/crawl-sites/unknown/crawl-runs"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.error.code").value("ERROR_001"))
			.andExpect(jsonPath("$.error.description").value("잘못된 요청입니다."))
			.andExpect(jsonPath("$.error.message").value("Unsupported crawler site code: unknown"));
	}
}
