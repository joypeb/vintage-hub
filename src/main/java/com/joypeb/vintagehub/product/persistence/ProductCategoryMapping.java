package com.joypeb.vintagehub.product.persistence;

import java.math.BigDecimal;
import java.util.Locale;

final class ProductCategoryMapping {

	private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.950");
	private static final BigDecimal MEDIUM_CONFIDENCE = new BigDecimal("0.800");

	private ProductCategoryMapping() {
	}

	static ProductCategoryMappingResult from(String siteCode, String sourceCategoryName) {
		if (!"rocketsalad".equals(siteCode) || isBlank(sourceCategoryName)) {
			return ProductCategoryMappingResult.empty();
		}
		String[] parts = normalizedParts(sourceCategoryName);
		String majorCategory = parts[0];
		String middleCategory = parts[1];
		return switch (majorCategory) {
			case "top" -> top(middleCategory);
			case "pants" -> new ProductCategoryMappingResult("하의", "팬츠", HIGH_CONFIDENCE);
			case "outer" -> new ProductCategoryMappingResult("아우터", null, MEDIUM_CONFIDENCE);
			case "acc" -> acc(middleCategory);
			default -> ProductCategoryMappingResult.empty();
		};
	}

	private static ProductCategoryMappingResult top(String middleCategory) {
		if ("sweat shirt".equals(middleCategory)) {
			return new ProductCategoryMappingResult("상의", "스웻", HIGH_CONFIDENCE);
		}
		if ("sweater".equals(middleCategory)) {
			return new ProductCategoryMappingResult("상의", "니트", HIGH_CONFIDENCE);
		}
		if ("vest".equals(middleCategory)) {
			return new ProductCategoryMappingResult("상의", "베스트", HIGH_CONFIDENCE);
		}
		if (middleCategory.contains("t shirt")) {
			return new ProductCategoryMappingResult("상의", "티셔츠", HIGH_CONFIDENCE);
		}
		if (middleCategory.contains("shirt")) {
			return new ProductCategoryMappingResult("상의", "셔츠", HIGH_CONFIDENCE);
		}
		return new ProductCategoryMappingResult("상의", null, MEDIUM_CONFIDENCE);
	}

	private static ProductCategoryMappingResult acc(String middleCategory) {
		return switch (middleCategory) {
			case "cap" -> new ProductCategoryMappingResult("액세서리", "모자", HIGH_CONFIDENCE);
			case "etc,mix" -> new ProductCategoryMappingResult("액세서리", "기타", HIGH_CONFIDENCE);
			case "shoes" -> new ProductCategoryMappingResult("신발", null, HIGH_CONFIDENCE);
			case "belt" -> new ProductCategoryMappingResult("액세서리", "벨트", HIGH_CONFIDENCE);
			case "jewelry" -> new ProductCategoryMappingResult("액세서리", "쥬얼리", HIGH_CONFIDENCE);
			case "neck tie" -> new ProductCategoryMappingResult("액세서리", "넥타이", HIGH_CONFIDENCE);
			case "suspender" -> new ProductCategoryMappingResult("액세서리", "서스펜더", HIGH_CONFIDENCE);
			default -> new ProductCategoryMappingResult("액세서리", null, MEDIUM_CONFIDENCE);
		};
	}

	private static String[] normalizedParts(String sourceCategoryName) {
		String normalized = sourceCategoryName
			.toLowerCase(Locale.ROOT)
			.replace("(대)", "")
			.replace("(중)", "")
			.replaceAll("\\s+", " ")
			.trim();
		String[] parts = normalized.split(">", 2);
		String majorCategory = parts[0].trim();
		String middleCategory = parts.length == 1 ? "" : parts[1].trim();
		return new String[] {majorCategory, middleCategory};
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	record ProductCategoryMappingResult(String standardCategory, String standardSubCategory,
			BigDecimal categoryConfidence) {

		private static ProductCategoryMappingResult empty() {
			return new ProductCategoryMappingResult(null, null, null);
		}
	}
}
