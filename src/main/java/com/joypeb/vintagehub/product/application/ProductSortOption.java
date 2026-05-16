package com.joypeb.vintagehub.product.application;

import org.springframework.data.domain.Sort;

public enum ProductSortOption {

	LATEST("최신순", Sort.by(
		Sort.Order.desc("collectedAt"),
		Sort.Order.desc("id")
	)),
	PRICE_LOW("가격 낮은순", Sort.by(
		Sort.Order.asc("displayPrice").nullsLast(),
		Sort.Order.desc("collectedAt"),
		Sort.Order.desc("id")
	)),
	PRICE_HIGH("가격 높은순", Sort.by(
		Sort.Order.desc("displayPrice").nullsLast(),
		Sort.Order.desc("collectedAt"),
		Sort.Order.desc("id")
	));

	private final String displayName;
	private final Sort sort;

	ProductSortOption(String displayName, Sort sort) {
		this.displayName = displayName;
		this.sort = sort;
	}

	public String displayName() {
		return displayName;
	}

	public Sort sort() {
		return sort;
	}
}
