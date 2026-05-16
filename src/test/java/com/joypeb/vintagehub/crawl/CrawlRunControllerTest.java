package com.joypeb.vintagehub.crawl;

import com.joypeb.vintagehub.crawl.application.CrawlRunEventPublisher;
import com.joypeb.vintagehub.crawl.application.CrawlRunAlreadyActiveException;
import com.joypeb.vintagehub.crawl.application.CrawlRunQueryService;
import com.joypeb.vintagehub.crawl.application.CrawlRunRequestService;
import com.joypeb.vintagehub.crawl.application.CrawlRunStatusResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlRunController.class)
class CrawlRunControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrawlRunRequestService crawlRunRequestService;

	@MockitoBean
	private CrawlRunQueryService crawlRunQueryService;

	@MockitoBean
	private CrawlRunEventPublisher crawlRunEventPublisher;

	@Test
	void requestCrawlRunStartsBackgroundRunAndReturnsRunId() throws Exception {
		when(crawlRunRequestService.requestManualRun("rocketsalad"))
			.thenReturn(new CrawlRunStatusResult(42L, "rocketsalad", "RUNNING", 0, 0, 0, 0, "Crawl run started."));

		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.runId").value(42))
			.andExpect(jsonPath("$.data.siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data.status").value("RUNNING"))
			.andExpect(jsonPath("$.data.foundCount").value(0))
			.andExpect(jsonPath("$.data.createdCount").value(0))
			.andExpect(jsonPath("$.data.updatedCount").value(0))
			.andExpect(jsonPath("$.data.failedCount").value(0))
			.andExpect(jsonPath("$.data.message").value("Crawl run started."))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void getCrawlRunReturnsCurrentStatusSnapshot() throws Exception {
		when(crawlRunQueryService.getRun(42L))
			.thenReturn(new CrawlRunStatusResult(42L, "rocketsalad", "RUNNING", 3, 1, 1, 0, "Processing pants-new"));

		mockMvc.perform(get("/api/admin/crawl-runs/42"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.runId").value(42))
			.andExpect(jsonPath("$.data.status").value("RUNNING"))
			.andExpect(jsonPath("$.data.foundCount").value(3))
			.andExpect(jsonPath("$.data.createdCount").value(1))
			.andExpect(jsonPath("$.data.updatedCount").value(1))
			.andExpect(jsonPath("$.data.failedCount").value(0))
			.andExpect(jsonPath("$.data.message").value("Processing pants-new"));
	}

	@Test
	void getActiveCrawlRunsReturnsCurrentStatusSnapshots() throws Exception {
		when(crawlRunQueryService.getActiveRuns())
			.thenReturn(List.of(new CrawlRunStatusResult(42L, "rocketsalad", "RUNNING", 3, 1, 1, 0,
				"Processing pants-new")));

		mockMvc.perform(get("/api/admin/crawl-runs/active"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data[0].runId").value(42))
			.andExpect(jsonPath("$.data[0].siteCode").value("rocketsalad"))
			.andExpect(jsonPath("$.data[0].status").value("RUNNING"))
			.andExpect(jsonPath("$.data[0].foundCount").value(3))
			.andExpect(jsonPath("$.data[0].message").value("Processing pants-new"));
	}

	@Test
	void subscribeCrawlRunEventsReturnsSseStream() throws Exception {
		SseEmitter emitter = new SseEmitter(30_000L);
		when(crawlRunEventPublisher.subscribe(42L)).thenReturn(emitter);

		mockMvc.perform(get("/api/admin/crawl-runs/42/events").accept(MediaType.TEXT_EVENT_STREAM))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
	}

	@Test
	void requestCrawlRunReturnsConflictWhenRunIsAlreadyActive() throws Exception {
		doThrow(new CrawlRunAlreadyActiveException("rocketsalad"))
			.when(crawlRunRequestService)
			.requestManualRun("rocketsalad");

		mockMvc.perform(post("/api/admin/crawl-sites/rocketsalad/crawl-runs"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("ERROR_005"))
			.andExpect(jsonPath("$.error.description").value("요청한 리소스 상태와 충돌합니다."))
			.andExpect(jsonPath("$.error.message").value("Crawl run is already active: rocketsalad"));
	}

	@Test
	void requestCrawlRunReturnsCommonErrorResponseForInvalidSiteCode() throws Exception {
		doThrow(new IllegalArgumentException("Unsupported crawler site code: unknown"))
			.when(crawlRunRequestService)
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
