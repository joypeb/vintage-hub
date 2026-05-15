package com.joypeb.vintagehub.product.application;

import java.util.List;

public record ProductFilterOptionsResult(
	List<SiteOption> sites,
	List<CategoryOption> categories,
	List<MeasurementOption> measurements
) {

	public ProductFilterOptionsResult {
		sites = List.copyOf(sites);
		categories = List.copyOf(categories);
		measurements = List.copyOf(measurements);
	}

	public record SiteOption(
		String code,
		String name,
		Long productCount
	) {
	}

	public record CategoryOption(
		String name,
		Long productCount,
		List<SubCategoryOption> subCategories
	) {

		public CategoryOption {
			subCategories = List.copyOf(subCategories);
		}
	}

	public record SubCategoryOption(
		String name,
		Long productCount
	) {
	}

	public record MeasurementOption(
		String part,
		Long productCount
	) {
	}
}
