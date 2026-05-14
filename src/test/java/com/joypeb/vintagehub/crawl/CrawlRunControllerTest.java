package com.joypeb.vintagehub.crawl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CrawlRunController.class)
class CrawlRunControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void requestCrawlRunAcceptsSiteCode() throws Exception {
		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.message").value("Crawl request accepted."));
	}
}
