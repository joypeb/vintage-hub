package com.joypeb.vintagehub.crawl.application;

public class CrawlRunAlreadyActiveException extends RuntimeException {

	public CrawlRunAlreadyActiveException(String siteCode) {
		super("Crawl run is already active: " + siteCode);
	}
}
