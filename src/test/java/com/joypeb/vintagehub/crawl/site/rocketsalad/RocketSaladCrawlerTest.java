package com.joypeb.vintagehub.crawl.site.rocketsalad;

import com.joypeb.vintagehub.crawl.domain.CrawlCursor;
import com.joypeb.vintagehub.crawl.domain.CrawlListResult;
import com.joypeb.vintagehub.crawl.domain.CrawlTargetSite;
import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductRef;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RocketSaladCrawlerTest {

	private final CrawlTargetSite site = new CrawlTargetSite("rocketsalad", URI.create("https://www.rocketsalad.co.kr"));

	@Test
	void initialCursorsCoverAllConfiguredCategories() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of()));

		assertThat(crawler.initialCursors()).extracting(CrawlCursor::value)
			.containsExactly("TOP:1", "OUTER:1", "PANTS:1", "ACC:1", "WOMEN:1");
	}

	@Test
	void fetchListParsesMobileProductCards() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of(
			"https://www.rocketsalad.co.kr/m/product_list.html?xcode=115&type=X&viewtype=gallery&page=2&sort=order",
			"""
			<html><body>
				<ul>
					<li class="item">
						<a href="/m/product.html?branduid=521529">
							<img class="MS_prod_mobile_image" src="/shopimages/yahoochina1/1150020027262.jpg">
							<span class="pname">90`s Levi`s 550 Relaxed Fit Denim Shorts (33)</span>
							<span class="price">55,000원</span>
						</a>
					</li>
					<li class="item">
						<a href="/m/product.html?branduid=521530">
							<img class="MS_prod_mobile_image" src="//cdn.example.test/item.jpg">
							<span class="pname">Vintage Shirt</span>
							<span class="sold-out">SOLD OUT</span>
						</a>
					</li>
				</ul>
			</body></html>
			"""
		)));

		CrawlListResult result = crawler.fetchList(site, new CrawlCursor("PANTS:2"));

		assertThat(result.products()).hasSize(2);
		assertThat(result.products().getFirst().ref().sourceProductId()).isEqualTo("521529");
		assertThat(result.products().getFirst().ref().detailUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521529"));
		assertThat(result.products().getFirst().name()).isEqualTo("90`s Levi`s 550 Relaxed Fit Denim Shorts (33)");
		assertThat(result.products().getFirst().thumbnailImageUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shopimages/yahoochina1/1150020027262.jpg"));
		assertThat(result.nextCursor()).isEqualTo(new CrawlCursor("PANTS:3"));
	}

	@Test
	void fetchListParsesProductCardWhenLinkOnlyWrapsDetailButton() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of(
			"https://www.rocketsalad.co.kr/m/product_list.html?xcode=113&type=X&viewtype=gallery&page=1&sort=order",
			"""
			<html><body>
				<ul class="items">
					<li>
						<dl>
							<dd class="thumb-img gallery-img">
								<img class="MS_prod_mobile_image" src="/shopimages/yahoochina1/1130020065543.jpg?1777801627">
							</dd>
							<dd class="pname">Gildan "Cookie Monster Ghostbusters Parody T (100)</dd>
							<dd class="link-view">
								<a href="/m/product.html?branduid=521219&xcode=113&type=X&sort=order">상품 자세히보기</a>
							</dd>
						</dl>
					</li>
				</ul>
			</body></html>
			"""
		)));

		CrawlListResult result = crawler.fetchList(site, new CrawlCursor("TOP:1"));

		assertThat(result.products()).hasSize(1);
		assertThat(result.products().getFirst().ref().sourceProductId()).isEqualTo("521219");
		assertThat(result.products().getFirst().name()).isEqualTo("Gildan \"Cookie Monster Ghostbusters Parody T (100)");
		assertThat(result.products().getFirst().thumbnailImageUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shopimages/yahoochina1/1130020065543.jpg?1777801627"));
	}

	@Test
	void fetchDetailParsesJsonLdAndDetailText() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of(
			"https://www.rocketsalad.co.kr/m/product.html?branduid=521529",
			"""
			<html>
			<head>
				<title>fallback title</title>
				<script type="application/ld+json">
				{
					"@context": "https://schema.org",
					"@type": "Product",
					"@id": "https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521529",
					"name": "90`s Levi`s 550 Relaxed Fit Denim Shorts (33)",
					"description": "리바이스 데님 쇼츠",
					"image": ["/shopimages/yahoochina1/1150020027262.jpg"],
					"category": "Pants > casual",
					"offers": {
						"priceCurrency": "KRW",
						"price": "55000",
						"availability": "https://schema.org/OutOfStock"
					}
				}
				</script>
			</head>
			<body>
				<input id="price" value="55000">
				<input id="disprice" value="">
				<div id="detail_img1">
					리바이스의 90년대모델 550 데님 쇼츠 입니다.
					Size: 허리 44cm 허벅지 36cm 밑위 34cm 기장 51cm
				</div>
				<div class="is_soldout">SOLD OUT</div>
			</body>
			</html>
			"""
		)));
		CrawledProductRef ref = new CrawledProductRef("521529",
			URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521529"));

		CrawledProductDetail detail = crawler.fetchDetail(site, ref);

		assertThat(detail.name()).isEqualTo("90`s Levi`s 550 Relaxed Fit Denim Shorts (33)");
		assertThat(detail.originalPrice()).isEqualByComparingTo("55000");
		assertThat(detail.salePrice()).isNull();
		assertThat(detail.availability()).isEqualTo(ProductAvailability.SOLD_OUT);
		assertThat(detail.description()).isEqualTo(
			"리바이스의 90년대모델 550 데님 쇼츠 입니다. Size: 허리 44cm 허벅지 36cm 밑위 34cm 기장 51cm");
		assertThat(detail.thumbnailImageUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shopimages/yahoochina1/1150020027262.jpg"));
		assertThat(detail.sourceCategoryName()).isEqualTo("Pants > casual");
		assertThat(detail.measurements()).containsEntry("허리", "44");
		assertThat(detail.measurements()).containsEntry("허벅지", "36");
		assertThat(detail.measurements()).containsEntry("밑위", "34");
		assertThat(detail.measurements()).containsEntry("기장", "51");
	}

	@Test
	void fetchDetailFallsBackToDetailDomWhenJsonLdCannotBeParsed() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of(
			"https://www.rocketsalad.co.kr/m/product.html?branduid=521638",
			"""
			<html>
			<head>
				<title>[M&M's Promo Print T (105)]</title>
				<script type="application/ld+json">
				{"@type": "Product", "name": "M&M\\'s Promo Print T (105)", "offers": {"price": 30000,}}
				</script>
			</head>
			<body>
				<div id="detail-item">
					<h2>M&M's Promo Print T (105)</h2>
					<div class="items">
						<img src="/shopimages/yahoochina1/1130020066322.jpg?1778756173">
					</div>
				</div>
				<input id="price" value="30,000">
				<input id="disprice" value="">
				<div id="detail_img1">Size: 가슴 58cm 어깨 58cm 기장 78cm</div>
			</body>
			</html>
			"""
		)));
		CrawledProductRef ref = new CrawledProductRef("521638",
			URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521638"));

		CrawledProductDetail detail = crawler.fetchDetail(site, ref);

		assertThat(detail.name()).isEqualTo("M&M's Promo Print T (105)");
		assertThat(detail.originalPrice()).isEqualByComparingTo("30000");
		assertThat(detail.thumbnailImageUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shopimages/yahoochina1/1130020066322.jpg?1778756173"));
		assertThat(detail.measurements()).containsEntry("가슴", "58");
		assertThat(detail.measurements()).containsEntry("어깨", "58");
		assertThat(detail.measurements()).containsEntry("기장", "78");
	}

	@Test
	void fetchDetailNormalizesRocketSaladJsonLdEscapedApostrophes() {
		RocketSaladCrawler crawler = new RocketSaladCrawler(new StubPageClient(Map.of(
			"https://www.rocketsalad.co.kr/m/product.html?branduid=521217",
			"""
			<html>
			<head>
				<title>[fallback title]</title>
				<script type="application/ld+json">
				{
					"@context": "https://schema.org",
					"@type": "Product",
					"name": "M&M\\'s Candy Promo Print T (105)",
					"image": ["https://www.rocketsalad.co.kr/shopimages/yahoochina1/1130020065592.jpg"],
					"category": "Top > 1/2 T shirt",
					"offers": {
						"price": 30000,
						"availability": "https://schema.org/InStock"
					}
				}
				</script>
			</head>
			<body>
				<input id="price" value="30,000">
				<div id="detail_img1">Size: 가슴 57cm 기장 76cm</div>
			</body>
			</html>
			"""
		)));
		CrawledProductRef ref = new CrawledProductRef("521217",
			URI.create("https://www.rocketsalad.co.kr/shop/shopdetail.html?branduid=521217"));

		CrawledProductDetail detail = crawler.fetchDetail(site, ref);

		assertThat(detail.name()).isEqualTo("M&M's Candy Promo Print T (105)");
		assertThat(detail.originalPrice()).isEqualByComparingTo("30000");
		assertThat(detail.availability()).isEqualTo(ProductAvailability.AVAILABLE);
		assertThat(detail.thumbnailImageUrl())
			.isEqualTo(URI.create("https://www.rocketsalad.co.kr/shopimages/yahoochina1/1130020065592.jpg"));
		assertThat(detail.sourceCategoryName()).isEqualTo("Top > 1/2 T shirt");
	}

	private record StubPageClient(Map<String, String> pages) implements RocketSaladPageClient {

		@Override
		public String get(URI uri) {
			return pages.get(uri.toString());
		}
	}
}
