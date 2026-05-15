package com.joypeb.vintagehub.docs;

import com.joypeb.vintagehub.crawl.application.CrawlRunResult;
import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OpenApiDocumentationTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@MockitoBean
	private CrawlRunService crawlRunService;

	@Test
	void exposesOpenApiDocsForCrawlRunApi() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
		when(crawlRunService.requestManualRun("rocketsalad"))
			.thenReturn(new CrawlRunResult("rocketsalad", "SUCCEEDED", 1, 1, 0, 0, "Crawl run completed."));

		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Vintage Hub API"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.paths['/api/products'].get").exists())
			.andExpect(jsonPath("$.paths['/api/admin/auth/login'].post").exists())
			.andExpect(jsonPath("$.paths['/api/admin/auth/password-hash'].post").exists())
			.andExpect(jsonPath("$.paths['/api/admin/crawl-sites/{siteCode}/crawl-runs'].post").exists());
	}
}
