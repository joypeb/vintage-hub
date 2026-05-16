package com.joypeb.vintagehub.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

	@Test
	void asyncRequestTimeoutReturnsNoContentWithoutErrorBody() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/crawl-runs/16/events");

		ResponseEntity<Void> response = exceptionHandler
			.handleAsyncRequestTimeoutException(new AsyncRequestTimeoutException(), request);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(response.getBody()).isNull();
	}
}
