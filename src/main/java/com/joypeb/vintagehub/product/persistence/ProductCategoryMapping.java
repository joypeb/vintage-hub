package com.joypeb.vintagehub.product.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

final class ProductCategoryMapping {

	private static final BigDecimal HIGH_CONFIDENCE = new BigDecimal("0.950");
	private static final BigDecimal MEDIUM_CONFIDENCE = new BigDecimal("0.800");
	private static final List<CategoryKeywordRule> KEYWORD_RULES = List.of(
		new CategoryKeywordRule(List.of("jacket", "자켓", "재킷", "blazer"), "아우터", "자켓", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("coat", "코트"), "아우터", "코트", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("jeans", "denim", "데님", "청바지"), "하의", "데님", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("pants", "pant", "trouser", "shorts", "팬츠", "바지", "쇼츠"), "하의", "팬츠",
			MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("skirt", "스커트"), "하의", "스커트", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("parka", "파카"), "아우터", null, MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("knit", "sweater", "니트"), "상의", "니트", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("sweat", "sweatshirt", "스웻", "스웨트"), "상의", "스웻", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("vest", "베스트"), "상의", "베스트", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("t-shirt", "t shirt", "print t", " t for", "티셔츠", "티셔트", "프린트 티"),
			"상의", "티셔츠", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("shirt", "셔츠"), "상의", "셔츠", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("cap", "캡", "모자"), "액세서리", "모자", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("belt", "벨트"), "액세서리", "벨트", MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("shoes", "shoe", "슈즈", "신발"), "신발", null, MEDIUM_CONFIDENCE),
		new CategoryKeywordRule(List.of("bag", "가방"), "가방", null, MEDIUM_CONFIDENCE)
	);

	private ProductCategoryMapping() {
	}

	static ProductCategoryMappingResult from(String siteCode, String sourceCategoryName) {
		return from(siteCode, sourceCategoryName, null, null);
	}

	static ProductCategoryMappingResult from(String siteCode, String sourceCategoryName, String productName,
			String description) {
		if (!"rocketsalad".equals(siteCode)) {
			// 현재 매핑 규칙은 로켓샐러드 카테고리 체계에만 맞춰져 있다.
			return ProductCategoryMappingResult.empty();
		}
		ProductCategoryMappingResult sourceCategoryMapping = fromSourceCategory(sourceCategoryName);
		if (!sourceCategoryMapping.isEmpty()) {
			// 원본 카테고리가 해석되면 키워드 추정보다 높은 신뢰도로 사용한다.
			return sourceCategoryMapping;
		}
		// 원본 카테고리가 없거나 알 수 없으면 상품명/설명 키워드로 보조 분류한다.
		return fromKeywordDictionary(productName, description);
	}

	private static ProductCategoryMappingResult fromSourceCategory(String sourceCategoryName) {
		if (isBlank(sourceCategoryName)) {
			return ProductCategoryMappingResult.empty();
		}
		// "TOP > SHIRT" 같은 원본 문자열을 대분류/중분류 두 조각으로 정규화한다.
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
		// TOP 대분류는 중분류 키워드로 세부 상의 카테고리를 판별한다.
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
		// ACC 안에는 신발/가방처럼 별도 표준 대분류로 보낼 항목도 포함된다.
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

	private static ProductCategoryMappingResult fromKeywordDictionary(String productName, String description) {
		String text = normalizeText(productName, description);
		for (CategoryKeywordRule rule : KEYWORD_RULES) {
			if (rule.matches(text)) {
				// 먼저 선언된 규칙을 우선 적용하므로 더 구체적인 키워드를 앞쪽에 둔다.
				return rule.toResult();
			}
		}
		return ProductCategoryMappingResult.empty();
	}

	private static String normalizeText(String... values) {
		StringBuilder builder = new StringBuilder();
		for (String value : values) {
			if (!isBlank(value)) {
				// 대소문자 차이를 없애고 상품명과 설명을 하나의 검색 문자열로 합친다.
				builder.append(' ').append(value.toLowerCase(Locale.ROOT));
			}
		}
		return builder.toString();
	}

	private static boolean containsAny(String text, String... keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	private static String[] normalizedParts(String sourceCategoryName) {
		// 로켓샐러드 카테고리의 "(대)", "(중)" 표기와 중복 공백을 제거한다.
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

	private record CategoryKeywordRule(List<String> keywords, String standardCategory, String standardSubCategory,
			BigDecimal categoryConfidence) {

		private boolean matches(String text) {
			// 규칙에 속한 키워드 중 하나라도 포함되면 해당 카테고리로 분류한다.
			return containsAny(text, keywords.toArray(String[]::new));
		}

		private ProductCategoryMappingResult toResult() {
			return new ProductCategoryMappingResult(standardCategory, standardSubCategory, categoryConfidence);
		}
	}

	record ProductCategoryMappingResult(String standardCategory, String standardSubCategory,
			BigDecimal categoryConfidence) {

		private boolean isEmpty() {
			return standardCategory == null && standardSubCategory == null && categoryConfidence == null;
		}

		private static ProductCategoryMappingResult empty() {
			return new ProductCategoryMappingResult(null, null, null);
		}
	}
}
