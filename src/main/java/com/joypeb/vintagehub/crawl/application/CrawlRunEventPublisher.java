package com.joypeb.vintagehub.crawl.application;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class CrawlRunEventPublisher {

	private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

	private final ConcurrentMap<Long, List<SseEmitter>> emittersByRunId = new ConcurrentHashMap<>();

	public SseEmitter subscribe(Long runId) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
		emittersByRunId.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
		emitter.onCompletion(() -> remove(runId, emitter));
		emitter.onTimeout(() -> completeTimedOutEmitter(runId, emitter));
		emitter.onError(ignored -> remove(runId, emitter));
		return emitter;
	}

	public void publish(CrawlRunStatusResult status) {
		List<SseEmitter> emitters = emittersByRunId.get(status.runId());
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			try {
				emitter.send(SseEmitter.event()
					.name("crawl-run-progress")
					.data(status));
			}
			catch (IOException | IllegalStateException exception) {
				remove(status.runId(), emitter);
			}
		}
	}

	private void completeTimedOutEmitter(Long runId, SseEmitter emitter) {
		remove(runId, emitter);
		try {
			emitter.complete();
		}
		catch (IllegalStateException ignored) {
			// The servlet container may have already completed the async response.
		}
	}

	private void remove(Long runId, SseEmitter emitter) {
		List<SseEmitter> emitters = emittersByRunId.get(runId);
		if (emitters == null) {
			return;
		}
		emitters.remove(emitter);
		if (emitters.isEmpty()) {
			emittersByRunId.remove(runId, emitters);
		}
	}
}
