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
		// 하나의 크롤링 실행을 여러 관리자 화면이 동시에 구독할 수 있게 runId별 목록에 보관한다.
		emittersByRunId.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
		// 연결 종료, 타임아웃, 에러가 발생하면 누수 방지를 위해 emitter를 제거한다.
		emitter.onCompletion(() -> remove(runId, emitter));
		emitter.onTimeout(() -> completeTimedOutEmitter(runId, emitter));
		emitter.onError(ignored -> remove(runId, emitter));
		return emitter;
	}

	public void publish(CrawlRunStatusResult status) {
		List<SseEmitter> emitters = emittersByRunId.get(status.runId());
		if (emitters == null || emitters.isEmpty()) {
			// 구독자가 없으면 이벤트 생성 비용 없이 종료한다.
			return;
		}
		for (SseEmitter emitter : emitters) {
			try {
				// 관리자 화면은 crawl-run-progress 이벤트 이름으로 진행 상태를 수신한다.
				emitter.send(SseEmitter.event()
					.name("crawl-run-progress")
					.data(status));
			}
			catch (IOException | IllegalStateException exception) {
				// 끊어진 SSE 연결은 다음 발행에서 다시 실패하지 않도록 즉시 정리한다.
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
			// 마지막 구독자가 사라지면 runId 키도 제거해 맵이 계속 커지지 않게 한다.
			emittersByRunId.remove(runId, emitters);
		}
	}
}
