package com.joypeb.vintagehub.crawl.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;

public record CrawledProductDetail(
	CrawledProductRef ref,
	String name,
	BigDecimal originalPrice,
	BigDecimal salePrice,
	ProductAvailability availability,
	String description,
	URI thumbnailImageUrl,
	String sourceCategoryName,
	Map<String, String> measurements
) {
}
