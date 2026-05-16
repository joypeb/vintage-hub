package com.joypeb.vintagehub.crawl.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "crawl_run")
public class CrawlRunEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id", nullable = false)
	private CrawlSiteEntity site;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CrawlTriggerType triggerType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CrawlRunStatus status;

	private Instant startedAt;

	private Instant finishedAt;

	@Column(nullable = false)
	private int foundCount;

	@Column(nullable = false)
	private int createdCount;

	@Column(nullable = false)
	private int updatedCount;

	@Column(nullable = false)
	private int failedCount;

	private String message;

	protected CrawlRunEntity() {
	}

	private CrawlRunEntity(CrawlSiteEntity site, CrawlTriggerType triggerType) {
		this.site = site;
		this.triggerType = triggerType;
		this.status = CrawlRunStatus.PENDING;
	}

	public static CrawlRunEntity manual(CrawlSiteEntity site) {
		return new CrawlRunEntity(site, CrawlTriggerType.MANUAL);
	}

	public static CrawlRunEntity scheduled(CrawlSiteEntity site) {
		return new CrawlRunEntity(site, CrawlTriggerType.SCHEDULED);
	}

	public void markRunning() {
		this.status = CrawlRunStatus.RUNNING;
		this.startedAt = Instant.now();
		this.message = "Crawl run started.";
	}

	public void markProgress(int foundCount, int createdCount, int updatedCount, int failedCount, String message) {
		this.foundCount = foundCount;
		this.createdCount = createdCount;
		this.updatedCount = updatedCount;
		this.failedCount = failedCount;
		this.message = message;
	}

	public void markSucceeded(int foundCount, int createdCount, int updatedCount, int failedCount, String message) {
		this.status = CrawlRunStatus.SUCCEEDED;
		this.finishedAt = Instant.now();
		this.foundCount = foundCount;
		this.createdCount = createdCount;
		this.updatedCount = updatedCount;
		this.failedCount = failedCount;
		this.message = message;
	}

	public void markFailed(String message) {
		this.status = CrawlRunStatus.FAILED;
		this.finishedAt = Instant.now();
		this.message = message;
	}

	public CrawlRunStatus status() {
		return status;
	}

	public Long id() {
		return id;
	}

	public CrawlSiteEntity site() {
		return site;
	}

	public int foundCount() {
		return foundCount;
	}

	public int createdCount() {
		return createdCount;
	}

	public int updatedCount() {
		return updatedCount;
	}

	public int failedCount() {
		return failedCount;
	}

	public String message() {
		return message;
	}
}
