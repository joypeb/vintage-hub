package com.joypeb.vintagehub.crawl.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.net.URI;
import java.time.Instant;

@Entity
@Table(name = "crawl_site")
public class CrawlSiteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String displayName;

	@Column(nullable = false)
	private String baseUrl;

	@Column(nullable = false)
	private String platform;

	@Column(nullable = false)
	private int crawlIntervalMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CrawlerStatus crawlerStatus;

	private Instant lastCrawledAt;

	private Instant lastChangedAt;

	private Instant lastChangeDetectedAt;

	@Column(nullable = false)
	private int consecutiveNoChangeCount;

	protected CrawlSiteEntity() {
	}

	private CrawlSiteEntity(String code, String displayName, URI baseUrl, String platform, int crawlIntervalMinutes) {
		this.code = code;
		this.displayName = displayName;
		this.baseUrl = baseUrl.toString();
		this.platform = platform;
		this.crawlIntervalMinutes = crawlIntervalMinutes;
		this.crawlerStatus = CrawlerStatus.ACTIVE;
	}

	public static CrawlSiteEntity create(String code, String displayName, URI baseUrl, String platform,
			int crawlIntervalMinutes) {
		return new CrawlSiteEntity(code, displayName, baseUrl, platform, crawlIntervalMinutes);
	}

	public void markCrawled(boolean changed) {
		Instant now = Instant.now();
		this.lastCrawledAt = now;
		if (changed) {
			this.lastChangedAt = now;
			this.lastChangeDetectedAt = now;
			this.consecutiveNoChangeCount = 0;
			return;
		}
		this.consecutiveNoChangeCount++;
	}

	public Long id() {
		return id;
	}

	public String code() {
		return code;
	}

	public String displayName() {
		return displayName;
	}

	public URI baseUrl() {
		return URI.create(baseUrl);
	}

	public String platform() {
		return platform;
	}

	public int crawlIntervalMinutes() {
		return crawlIntervalMinutes;
	}

	public CrawlerStatus crawlerStatus() {
		return crawlerStatus;
	}

	public Instant lastCrawledAt() {
		return lastCrawledAt;
	}

	public Instant lastChangedAt() {
		return lastChangedAt;
	}

	public Instant lastChangeDetectedAt() {
		return lastChangeDetectedAt;
	}
}
