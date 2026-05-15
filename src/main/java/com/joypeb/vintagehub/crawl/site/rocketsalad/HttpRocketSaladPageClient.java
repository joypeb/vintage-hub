package com.joypeb.vintagehub.crawl.site.rocketsalad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;

@Component
class HttpRocketSaladPageClient implements RocketSaladPageClient {

	private static final Logger log = LoggerFactory.getLogger(HttpRocketSaladPageClient.class);
	private static final Charset EUC_KR = Charset.forName("EUC-KR");
	private static final String USER_AGENT = "VintageHubCrawler/0.1 (+https://vintage-hub.local)";
	private static final int MAX_REDIRECTS = 5;

	private final HttpClient httpClient;

	HttpRocketSaladPageClient() {
		this(HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.version(HttpClient.Version.HTTP_1_1)
			.build());
	}

	HttpRocketSaladPageClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String get(URI uri) {
		try {
			URI currentUri = uri;
			for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
				log.atDebug()
					.addKeyValue("event", "crawl.rocketsalad.http.request.started")
					.addKeyValue("url", currentUri)
					.addKeyValue("redirectCount", redirectCount)
					.log("crawl.rocketsalad.http.request.started");
				HttpResponse<byte[]> response = httpClient.send(request(currentUri), HttpResponse.BodyHandlers.ofByteArray());
				log.atDebug()
					.addKeyValue("event", "crawl.rocketsalad.http.response.received")
					.addKeyValue("url", currentUri)
					.addKeyValue("status", response.statusCode())
					.addKeyValue("bodyBytes", response.body().length)
					.log("crawl.rocketsalad.http.response.received");
				if (isRedirect(response.statusCode())) {
					Optional<URI> redirectedUri = redirectedUri(currentUri, response);
					if (redirectedUri.isEmpty()) {
						log.atWarn()
							.addKeyValue("event", "crawl.rocketsalad.http.redirect.stopped")
							.addKeyValue("url", currentUri)
							.addKeyValue("status", response.statusCode())
							.addKeyValue("reason", "empty-location-body")
							.log("crawl.rocketsalad.http.redirect.stopped");
						return new String(response.body(), EUC_KR);
					}
					log.atInfo()
						.addKeyValue("event", "crawl.rocketsalad.http.redirected")
						.addKeyValue("fromUrl", currentUri)
						.addKeyValue("toUrl", redirectedUri.get())
						.addKeyValue("status", response.statusCode())
						.addKeyValue("redirectCount", redirectCount + 1)
						.log("crawl.rocketsalad.http.redirected");
					currentUri = redirectedUri.get();
					continue;
				}
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					log.atWarn()
						.addKeyValue("event", "crawl.rocketsalad.http.request.failed")
						.addKeyValue("url", currentUri)
						.addKeyValue("status", response.statusCode())
						.log("crawl.rocketsalad.http.request.failed");
					throw new IllegalStateException("Rocket Salad request failed: " + response.statusCode() + " " + currentUri);
				}
				return new String(response.body(), EUC_KR);
			}
			log.atWarn()
				.addKeyValue("event", "crawl.rocketsalad.http.redirect.failed")
				.addKeyValue("url", uri)
				.addKeyValue("reason", "too-many-redirects")
				.addKeyValue("maxRedirects", MAX_REDIRECTS)
				.log("crawl.rocketsalad.http.redirect.failed");
			throw new IllegalStateException("Rocket Salad request failed: too many redirects " + uri);
		}
		catch (IOException exception) {
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "crawl.rocketsalad.http.request.failed")
				.addKeyValue("url", uri)
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.rocketsalad.http.request.failed");
			throw new IllegalStateException("Rocket Salad request failed: " + uri, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			log.atWarn()
				.setCause(exception)
				.addKeyValue("event", "crawl.rocketsalad.http.request.interrupted")
				.addKeyValue("url", uri)
				.addKeyValue("reason", exception.getMessage())
				.log("crawl.rocketsalad.http.request.interrupted");
			throw new IllegalStateException("Rocket Salad request interrupted: " + uri, exception);
		}
	}

	private HttpRequest request(URI uri) {
		return HttpRequest.newBuilder(uri)
			.version(HttpClient.Version.HTTP_1_1)
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml")
			.GET()
			.build();
	}

	private boolean isRedirect(int statusCode) {
		return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
	}

	private Optional<URI> redirectedUri(URI currentUri, HttpResponse<byte[]> response) {
		return response.headers().firstValue("location")
			.map(currentUri::resolve)
			.or(() -> redirectedUriWithoutLocation(currentUri, response));
	}

	private Optional<URI> redirectedUriWithoutLocation(URI currentUri, HttpResponse<byte[]> response) {
		if (response.body().length > 0) {
			return Optional.empty();
		}
		throw new IllegalStateException("Rocket Salad request failed: redirect without Location " + currentUri);
	}
}
