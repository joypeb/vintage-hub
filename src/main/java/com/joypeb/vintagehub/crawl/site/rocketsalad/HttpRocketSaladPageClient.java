package com.joypeb.vintagehub.crawl.site.rocketsalad;

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
				HttpResponse<byte[]> response = httpClient.send(request(currentUri), HttpResponse.BodyHandlers.ofByteArray());
				if (isRedirect(response.statusCode())) {
					Optional<URI> redirectedUri = redirectedUri(currentUri, response);
					if (redirectedUri.isEmpty()) {
						return new String(response.body(), EUC_KR);
					}
					currentUri = redirectedUri.get();
					continue;
				}
				if (response.statusCode() < 200 || response.statusCode() >= 300) {
					throw new IllegalStateException("Rocket Salad request failed: " + response.statusCode() + " " + currentUri);
				}
				return new String(response.body(), EUC_KR);
			}
			throw new IllegalStateException("Rocket Salad request failed: too many redirects " + uri);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Rocket Salad request failed: " + uri, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
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
