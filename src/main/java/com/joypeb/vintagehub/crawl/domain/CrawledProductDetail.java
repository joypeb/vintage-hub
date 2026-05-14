package com.joypeb.vintagehub.crawl.domain;

import java.math.BigDecimal;

public record CrawledProductDetail(
	CrawledProductRef ref,
	String name,
	BigDecimal originalPrice,
	BigDecimal salePrice,
	ProductAvailability availability
) {
}
