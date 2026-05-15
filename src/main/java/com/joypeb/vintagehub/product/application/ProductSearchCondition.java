package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.crawl.domain.ProductAvailability;

import java.math.BigDecimal;

public record ProductSearchCondition(
	String siteCode,
	String standardCategory,
	String standardSubCategory,
	ProductAvailability stockStatus,
	BigDecimal minPrice,
	BigDecimal maxPrice,
	String measurementPart,
	BigDecimal minMeasurement,
	BigDecimal maxMeasurement
) {
}
