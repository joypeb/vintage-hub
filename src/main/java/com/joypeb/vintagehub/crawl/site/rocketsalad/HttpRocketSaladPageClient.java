package com.joypeb.vintagehub.crawl.site.rocketsalad;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;

@Component
class HttpRocketSaladPageClient implements RocketSaladPageClient {

	private static final Charset EUC_KR = Charset.forName("EUC-KR");
	private static final String USER_AGENT = "VintageHubCrawler/0.1 (+https://vintage-hub.local)";

	private final HttpClient httpClient;

	HttpRocketSaladPageClient() {
		this(HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build());
	}

	HttpRocketSaladPageClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String get(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(Duration.ofSeconds(20))
			.header("User-Agent", USER_AGENT)
			.header("Accept", "text/html,application/xhtml+xml")
			.GET()
			.build();

		try {
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("Rocket Salad request failed: " + response.statusCode() + " " + uri);
			}
			return new String(response.body(), EUC_KR);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Rocket Salad request failed: " + uri, exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Rocket Salad request interrupted: " + uri, exception);
		}
	}
}
