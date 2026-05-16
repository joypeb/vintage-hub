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
		// 크롤링 실행 여부는 변경 감지 여부와 무관하게 항상 갱신한다.
		this.lastCrawledAt = now;
		if (changed) {
			// 신규/수정 상품이 있으면 변경 시각을 갱신하고 무변경 누적 횟수를 초기화한다.
			this.lastChangedAt = now;
			this.lastChangeDetectedAt = now;
			this.consecutiveNoChangeCount = 0;
			return;
		}
		// 수집은 되었지만 상품 변화가 없으면 스케줄 판단에 쓸 무변경 카운트를 증가시킨다.
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
		// DB에는 문자열로 보관하고 도메인 로직에서는 URI 타입으로 다룬다.
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
