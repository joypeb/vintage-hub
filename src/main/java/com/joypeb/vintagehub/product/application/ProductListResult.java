package com.joypeb.vintagehub.product.application;

import org.springframework.data.domain.Page;

import java.util.List;

public record ProductListResult(
	List<ProductListItem> content,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static ProductListResult from(Page<ProductListItem> page) {
		return new ProductListResult(
			page.getContent(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}
}
