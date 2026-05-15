package com.joypeb.vintagehub.auth;

import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"vintage-hub.admin.password-hash-api-enabled=false",
		"vintage-hub.jwt.secret=test-secret-key-test-secret-key-test-secret-key"
})
@AutoConfigureMockMvc
class AdminPasswordHashDisabledIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrawlRunService crawlRunService;

	@Test
	void passwordHashEndpointReturnsForbiddenWhenDisabled() throws Exception {
		mockMvc.perform(post("/api/admin/auth/password-hash")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "password": "new-admin-secret"
						}
						"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_004"));
	}
}
