package com.joypeb.vintagehub.crawl.site.rocketsalad;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRocketSaladPageClientTest {

	@Test
	void followsRelativeRedirectsManually() {
		FakeHttpClient httpClient = new FakeHttpClient(List.of(
			response(302, "", "/m/product_list.html?xcode=118&type=X&viewtype=gallery&page=1&sort=order"),
			response(200, "상품 목록", null)
		));
		HttpRocketSaladPageClient pageClient = new HttpRocketSaladPageClient(httpClient);

		String body = pageClient.get(URI.create(
			"https://www.rocketsalad.co.kr/m/product_list.html?xcode=118&type=X&viewtype=gallery&page=1&sort=order"));

		assertThat(body).isEqualTo("상품 목록");
		assertThat(httpClient.requestUris).containsExactly(
			URI.create("https://www.rocketsalad.co.kr/m/product_list.html?xcode=118&type=X&viewtype=gallery&page=1&sort=order"),
			URI.create("https://www.rocketsalad.co.kr/m/product_list.html?xcode=118&type=X&viewtype=gallery&page=1&sort=order")
		);
		assertThat(httpClient.requestVersions).containsOnly(HttpClient.Version.HTTP_1_1);
	}

	@Test
	void returnsBodyWhenRedirectHasNoLocation() {
		FakeHttpClient httpClient = new FakeHttpClient(List.of(
			response(302, "상품 목록", null)
		));
		HttpRocketSaladPageClient pageClient = new HttpRocketSaladPageClient(httpClient);

		String body = pageClient.get(URI.create(
			"https://www.rocketsalad.co.kr/m/product_list.html?xcode=115&type=X&viewtype=gallery&page=2&sort=order"));

		assertThat(body).isEqualTo("상품 목록");
	}

	private static HttpResponse<byte[]> response(int statusCode, String body, String location) {
		return new FakeResponse(statusCode, body.getBytes(Charset.forName("EUC-KR")), location);
	}

	private static class FakeHttpClient extends HttpClient {

		private final ArrayDeque<HttpResponse<byte[]>> responses;
		private final List<URI> requestUris = new java.util.ArrayList<>();
		private final List<Version> requestVersions = new java.util.ArrayList<>();

		private FakeHttpClient(List<HttpResponse<byte[]>> responses) {
			this.responses = new ArrayDeque<>(responses);
		}

		@Override
		public Optional<CookieHandler> cookieHandler() {
			return Optional.empty();
		}

		@Override
		public Optional<Duration> connectTimeout() {
			return Optional.empty();
		}

		@Override
		public Redirect followRedirects() {
			return Redirect.NEVER;
		}

		@Override
		public Optional<ProxySelector> proxy() {
			return Optional.empty();
		}

		@Override
		public SSLContext sslContext() {
			return null;
		}

		@Override
		public SSLParameters sslParameters() {
			return null;
		}

		@Override
		public Optional<Authenticator> authenticator() {
			return Optional.empty();
		}

		@Override
		public Version version() {
			return Version.HTTP_1_1;
		}

		@Override
		public Optional<Executor> executor() {
			return Optional.empty();
		}

		@Override
		public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
				throws IOException, InterruptedException {
			requestUris.add(request.uri());
			requestVersions.add(request.version().orElse(null));
			@SuppressWarnings("unchecked")
			HttpResponse<T> response = (HttpResponse<T>) responses.removeFirst();
			return response;
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
				HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
			throw new UnsupportedOperationException();
		}
	}

	private record FakeResponse(int statusCode, byte[] body, String location) implements HttpResponse<byte[]> {

		@Override
		public HttpRequest request() {
			return null;
		}

		@Override
		public Optional<HttpResponse<byte[]>> previousResponse() {
			return Optional.empty();
		}

		@Override
		public HttpHeaders headers() {
			if (location == null) {
				return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
			}
			return HttpHeaders.of(java.util.Map.of("location", List.of(location)), (name, value) -> true);
		}

		@Override
		public byte[] body() {
			return body;
		}

		@Override
		public Optional<SSLSession> sslSession() {
			return Optional.empty();
		}

		@Override
		public URI uri() {
			return null;
		}

		@Override
		public HttpClient.Version version() {
			return HttpClient.Version.HTTP_1_1;
		}
	}
}
