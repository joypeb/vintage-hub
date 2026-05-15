package com.joypeb.vintagehub.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joypeb.vintagehub.crawl.application.CrawlRunResult;
import com.joypeb.vintagehub.crawl.application.CrawlRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"vintage-hub.admin.password-hash-api-enabled=true",
		"vintage-hub.jwt.secret=test-secret-key-test-secret-key-test-secret-key",
		"vintage-hub.jwt.access-token-validity-seconds=3600"
})
@AutoConfigureMockMvc
class AdminAuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private CrawlRunService crawlRunService;

	@Autowired
	private AdminUserRepository adminUserRepository;

	@BeforeEach
	void setUp() {
		adminUserRepository.deleteAll();
		adminUserRepository.save(AdminUserEntity.create("admin", "{noop}admin-secret", true));
		adminUserRepository.save(AdminUserEntity.create("operator", "{noop}operator-secret", true));
		adminUserRepository.save(AdminUserEntity.create("disabled", "{noop}disabled-secret", false));
	}

	@Test
	void loginReturnsJwtForValidAdminCredentials() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "admin-secret"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.data.expiresIn").value(3600))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void loginReturnsJwtForAnotherAdminUser() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "operator",
						  "password": "operator-secret"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.data.accessToken").isNotEmpty());
	}

	@Test
	void loginRejectsInvalidAdminCredentials() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "wrong"
						}
						"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_003"));
	}

	@Test
	void loginRejectsDisabledAdminUser() throws Exception {
		mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "disabled",
						  "password": "disabled-secret"
						}
						"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_003"));
	}

	@Test
	void passwordHashEndpointReturnsBcryptHashWhenEnabled() throws Exception {
		String response = mockMvc.perform(post("/api/admin/auth/password-hash")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "password": "new-admin-secret"
						}
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.passwordHash").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode root = objectMapper.readTree(response);
		assertThat(root.path("data").path("passwordHash").asText()).startsWith("{bcrypt}");
	}

	@Test
	void adminApiRejectsRequestWithoutJwt() throws Exception {
		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_003"));
	}

	@Test
	void adminApiAcceptsRequestWithJwt() throws Exception {
		when(crawlRunService.requestManualRun("rocketsalad"))
			.thenReturn(new CrawlRunResult("rocketsalad", "SUCCEEDED", 2, 1, 1, 0, "Crawl run completed."));
		String loginResponse = mockMvc.perform(post("/api/admin/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "username": "admin",
						  "password": "admin-secret"
						}
						"""))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String accessToken = objectMapper.readTree(loginResponse).path("data").path("accessToken").asText();

		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs")
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.siteCode").value("rocketsalad"));
	}
}
