package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.crawl.domain.ProductAvailability;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchCondition(
	String siteCode,
	String standardCategory,
	String standardSubCategory,
	ProductAvailability stockStatus,
	BigDecimal minPrice,
	BigDecimal maxPrice,
	List<MeasurementFilter> measurementFilters
) {

	public ProductSearchCondition {
		measurementFilters = measurementFilters == null ? List.of() : List.copyOf(measurementFilters);
	}

	public record MeasurementFilter(
		String part,
		BigDecimal minMeasurement,
		BigDecimal maxMeasurement
	) {
	}
}
