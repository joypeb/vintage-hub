package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.persistence.MeasurementSource;
import com.joypeb.vintagehub.product.persistence.ProductEntity;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDetailResult(
	Long id,
	String sourceProductId,
	String name,
	BigDecimal originalPrice,
	BigDecimal salePrice,
	BigDecimal displayPrice,
	String description,
	String detailUrl,
	String thumbnailImageUrl,
	String siteCode,
	String siteName,
	String standardCategory,
	String standardSubCategory,
	BigDecimal categoryConfidence,
	ProductAvailability stockStatus,
	Instant collectedAt,
	Instant lastSeenAt,
	Instant availabilityCheckedAt,
	List<Measurement> measurements
) {

	public static ProductDetailResult from(ProductEntity product, List<ProductMeasurementEntity> measurements) {
		return new ProductDetailResult(
			product.id(),
			product.sourceProductId(),
			product.name(),
			product.originalPrice(),
			product.salePrice(),
			displayPrice(product),
			product.description(),
			product.detailUrl(),
			product.thumbnailImageUrl(),
			product.site().code(),
			product.site().displayName(),
			product.standardCategory(),
			product.standardSubCategory(),
			product.categoryConfidence(),
			product.stockStatus(),
			product.collectedAt(),
			product.lastSeenAt(),
			product.availabilityCheckedAt(),
			measurements.stream().map(Measurement::from).toList()
		);
	}

	private static BigDecimal displayPrice(ProductEntity product) {
		if (product.salePrice() != null) {
			return product.salePrice();
		}
		return product.originalPrice();
	}

	public record Measurement(
		String part,
		BigDecimal valueCm,
		String rawText,
		BigDecimal confidence,
		MeasurementSource source,
		Instant updatedAt
	) {

		private static Measurement from(ProductMeasurementEntity measurement) {
			return new Measurement(
				measurement.part(),
				measurement.valueCm(),
				measurement.rawText(),
				measurement.confidence(),
				measurement.source(),
				measurement.updatedAt()
			);
		}
	}
}
