package com.joypeb.vintagehub.product.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ProductCategoryMappingTest {

	@Test
	void mapsRocketSaladSourceCategoriesToStandardCategories() {
		assertThat(List.of(
			row("Acc (대) > Etc,Mix (중)"),
			row("Outer (대) > western,hippie (중)"),
			row("Outer (대) > leisure (중)"),
			row("Outer (대) > mixed casual (중)"),
			row("Womancloth (대) > All Show (중)"),
			row("Outer (대) > british classic (중)"),
			row("Pants (대) > military (중)"),
			row("Outer (대) > military (중)"),
			row("Outer (대) > oldskool (중)"),
			row("Acc (대) > Cap (중)"),
			row("Top (대) > 1/2  T shirt (중)"),
			row("Pants (대) > work (중)"),
			row("Outer (대) > work (중)"),
			row("Top (대) > sweat shirt (중)"),
			row("Pants (대) > casual (중)"),
			row("Top (대) > sweater (중)"),
			row("Top (대) > vest (중)"),
			row("Top (대) > shirt (중)"),
			row("Top (대) > 1/2 summershirt (중)")
		))
			.extracting(CategoryRow::sourceCategoryName, CategoryRow::standardCategory,
				CategoryRow::standardSubCategory, CategoryRow::categoryConfidence)
			.containsExactly(
				tuple("Acc (대) > Etc,Mix (중)", "액세서리", "기타", new BigDecimal("0.950")),
				tuple("Outer (대) > western,hippie (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Outer (대) > leisure (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Outer (대) > mixed casual (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Womancloth (대) > All Show (중)", null, null, null),
				tuple("Outer (대) > british classic (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Pants (대) > military (중)", "하의", "팬츠", new BigDecimal("0.950")),
				tuple("Outer (대) > military (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Outer (대) > oldskool (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Acc (대) > Cap (중)", "액세서리", "모자", new BigDecimal("0.950")),
				tuple("Top (대) > 1/2  T shirt (중)", "상의", "티셔츠", new BigDecimal("0.950")),
				tuple("Pants (대) > work (중)", "하의", "팬츠", new BigDecimal("0.950")),
				tuple("Outer (대) > work (중)", "아우터", null, new BigDecimal("0.800")),
				tuple("Top (대) > sweat shirt (중)", "상의", "스웻", new BigDecimal("0.950")),
				tuple("Pants (대) > casual (중)", "하의", "팬츠", new BigDecimal("0.950")),
				tuple("Top (대) > sweater (중)", "상의", "니트", new BigDecimal("0.950")),
				tuple("Top (대) > vest (중)", "상의", "베스트", new BigDecimal("0.950")),
				tuple("Top (대) > shirt (중)", "상의", "셔츠", new BigDecimal("0.950")),
				tuple("Top (대) > 1/2 summershirt (중)", "상의", "셔츠", new BigDecimal("0.950"))
			);
	}

	@Test
	void leavesOtherSitesUnmapped() {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("other",
			"Top (대) > shirt (중)");

		assertThat(result.standardCategory()).isNull();
		assertThat(result.standardSubCategory()).isNull();
		assertThat(result.categoryConfidence()).isNull();
	}

	private static CategoryRow row(String sourceCategoryName) {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			sourceCategoryName);
		return new CategoryRow(sourceCategoryName, result.standardCategory(), result.standardSubCategory(),
			result.categoryConfidence());
	}

	private record CategoryRow(String sourceCategoryName, String standardCategory, String standardSubCategory,
			BigDecimal categoryConfidence) {
	}
}
