package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.persistence.ProductEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductListItem(
	Long id,
	String sourceProductId,
	String name,
	BigDecimal originalPrice,
	BigDecimal salePrice,
	BigDecimal displayPrice,
	String detailUrl,
	String thumbnailImageUrl,
	String siteCode,
	String siteName,
	String standardCategory,
	String standardSubCategory,
	ProductAvailability stockStatus,
	Instant collectedAt
) {

	public static ProductListItem from(ProductEntity product) {
		return new ProductListItem(
			product.id(),
			product.sourceProductId(),
			product.name(),
			product.originalPrice(),
			product.salePrice(),
			displayPrice(product),
			product.detailUrl(),
			product.thumbnailImageUrl(),
			product.site().code(),
			product.site().displayName(),
			product.standardCategory(),
			product.standardSubCategory(),
			product.stockStatus(),
			product.collectedAt()
		);
	}

	private static BigDecimal displayPrice(ProductEntity product) {
		if (product.displayPrice() != null) {
			return product.displayPrice();
		}
		if (product.salePrice() != null) {
			return product.salePrice();
		}
		return product.originalPrice();
	}
}
