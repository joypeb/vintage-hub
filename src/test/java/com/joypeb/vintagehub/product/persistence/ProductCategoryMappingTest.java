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

	@Test
	void mapsRocketSaladWomanclothByProductText() {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			"Womancloth (대) > All Show (중)", "~70`s French Faded Chore Jacket For Women",
			"70 년대 이전 무렵 빈티지모델 프렌치 워크웨어 초어 자켓 입니다.");

		assertThat(result.standardCategory()).isEqualTo("아우터");
		assertThat(result.standardSubCategory()).isEqualTo("자켓");
		assertThat(result.categoryConfidence()).isEqualByComparingTo("0.800");
	}

	@Test
	void fallsBackToKeywordDictionaryWhenSourceCategoryIsUnclear() {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			"Unknown (대) > All Show (중)", "Vintage Levi's 501 jeans 팬츠",
			"오리지널 데님 바지 입니다.");

		assertThat(result.standardCategory()).isEqualTo("하의");
		assertThat(result.standardSubCategory()).isEqualTo("데님");
		assertThat(result.categoryConfidence()).isEqualByComparingTo("0.800");
	}

	@Test
	void keepsSourceCategoryMappingBeforeKeywordDictionary() {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			"Pants (대) > military (중)", "Military Field Jacket", "자켓 형태의 포켓 디테일이 있습니다.");

		assertThat(result.standardCategory()).isEqualTo("하의");
		assertThat(result.standardSubCategory()).isEqualTo("팬츠");
		assertThat(result.categoryConfidence()).isEqualByComparingTo("0.950");
	}

	@Test
	void mapsRocketSaladWomanclothPrintTToTShirt() {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			"Womancloth (대) > All Show (중)", "Gildan \"Nirvana '93 In Utero Reprint T For Women",
			"미국발, 인기의 밴드 프린트 티 입니다. 100% 코튼");

		assertThat(result.standardCategory()).isEqualTo("상의");
		assertThat(result.standardSubCategory()).isEqualTo("티셔츠");
		assertThat(result.categoryConfidence()).isEqualByComparingTo("0.800");
	}

	@Test
	void mapsRocketSaladWomanclothVestAndParka() {
		assertThat(List.of(
			row("Womancloth (대) > All Show (중)", "Harley Davidson Leather Vest For Women", "레더 베스트 입니다."),
			row("Womancloth (대) > All Show (중)", "Barbour Waxed Flight Parka For Women", "왁스드 파카 입니다.")
		))
			.extracting(CategoryRow::standardCategory, CategoryRow::standardSubCategory,
				CategoryRow::categoryConfidence)
			.containsExactly(
				tuple("상의", "베스트", new BigDecimal("0.800")),
				tuple("아우터", null, new BigDecimal("0.800"))
			);
	}

	private static CategoryRow row(String sourceCategoryName) {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			sourceCategoryName);
		return new CategoryRow(sourceCategoryName, result.standardCategory(), result.standardSubCategory(),
			result.categoryConfidence());
	}

	private static CategoryRow row(String sourceCategoryName, String productName, String description) {
		ProductCategoryMapping.ProductCategoryMappingResult result = ProductCategoryMapping.from("rocketsalad",
			sourceCategoryName, productName, description);
		return new CategoryRow(sourceCategoryName, result.standardCategory(), result.standardSubCategory(),
			result.categoryConfidence());
	}

	private record CategoryRow(String sourceCategoryName, String standardCategory, String standardSubCategory,
			BigDecimal categoryConfidence) {
	}
}
