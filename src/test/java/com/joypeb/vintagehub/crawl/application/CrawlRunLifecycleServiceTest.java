package com.joypeb.vintagehub.crawl.application;

import com.joypeb.vintagehub.crawl.persistence.CrawlRunEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlRunStatus;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteRepository;
import com.joypeb.vintagehub.crawl.persistence.CrawlTriggerType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrawlRunLifecycleServiceTest {

	@Test
	void startRunPersistsRunningRunAndPublishesStatus() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		CrawlRunEventPublisher eventPublisher = mock(CrawlRunEventPublisher.class);
		CrawlRunLifecycleService service = new CrawlRunLifecycleService(siteRepository, runRepository, eventPublisher);
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		when(runRepository.existsBySite_CodeAndStatusIn(eq("rocketsalad"), any())).thenReturn(false);
		when(siteRepository.findByCode("rocketsalad")).thenReturn(Optional.of(site));
		when(runRepository.save(any())).thenAnswer(invocation -> {
			CrawlRunEntity run = invocation.getArgument(0);
			ReflectionTestUtils.setField(run, "id", 42L);
			return run;
		});

		CrawlRunStatusResult result = service.startRun("rocketsalad", CrawlTriggerType.MANUAL);

		assertThat(result.runId()).isEqualTo(42L);
		assertThat(result.status()).isEqualTo(CrawlRunStatus.RUNNING.name());
		assertThat(result.foundCount()).isZero();
		assertThat(result.message()).isEqualTo("Crawl run started.");
		verify(eventPublisher).publish(result);
	}

	@Test
	void markProgressPersistsCountsAndPublishesStatus() {
		CrawlSiteRepository siteRepository = mock(CrawlSiteRepository.class);
		CrawlRunRepository runRepository = mock(CrawlRunRepository.class);
		CrawlRunEventPublisher eventPublisher = mock(CrawlRunEventPublisher.class);
		CrawlRunLifecycleService service = new CrawlRunLifecycleService(siteRepository, runRepository, eventPublisher);
		CrawlSiteEntity site = CrawlSiteEntity.create("rocketsalad", "로켓샐러드", URI.create("https://www.rocketsalad.co.kr"), "MakeShop", 60);
		CrawlRunEntity run = CrawlRunEntity.manual(site);
		ReflectionTestUtils.setField(run, "id", 42L);
		run.markRunning();
		when(runRepository.findById(42L)).thenReturn(Optional.of(run));

		CrawlRunStatusResult result = service.markProgress(42L, 3, 1, 1, 0, "Processing pants-new");

		assertThat(result.status()).isEqualTo(CrawlRunStatus.RUNNING.name());
		assertThat(result.foundCount()).isEqualTo(3);
		assertThat(result.createdCount()).isEqualTo(1);
		assertThat(result.updatedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isZero();
		assertThat(result.message()).isEqualTo("Processing pants-new");
		verify(eventPublisher).publish(result);
	}
}
