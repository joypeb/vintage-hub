package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.application.ProductDetailResult;
import com.joypeb.vintagehub.product.application.ProductListResult;
import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import com.joypeb.vintagehub.product.application.ProductSearchService;
import com.joypeb.vintagehub.product.application.ProductSortOption;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
class ProductListController {

	private static final CacheControl PRODUCT_LIST_CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(30))
		.mustRevalidate();
	private static final CacheControl PRODUCT_DETAIL_CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(60))
		.mustRevalidate();

	private final ProductSearchService productSearchService;

	ProductListController(ProductSearchService productSearchService) {
		this.productSearchService = productSearchService;
	}

	@GetMapping
	ResponseEntity<ApiResponse<ProductListResult>> listProducts(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String siteCode,
			@RequestParam(required = false) String standardCategory,
			@RequestParam(required = false) String standardSubCategory,
			@RequestParam(required = false) ProductAvailability stockStatus,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) String measurementPart,
			@RequestParam(required = false) BigDecimal minMeasurement,
			@RequestParam(required = false) BigDecimal maxMeasurement,
			@RequestParam(required = false) List<String> measurementFilters,
			@RequestParam(defaultValue = "LATEST") ProductSortOption sort,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		// 기존 단일 실측 파라미터와 반복 가능한 measurementFilters 파라미터를 하나의 검색 조건으로 합친다.
		ProductSearchCondition condition = new ProductSearchCondition(keyword, siteCode, standardCategory, standardSubCategory,
			stockStatus, minPrice, maxPrice,
			parseMeasurementFilters(measurementFilters, measurementPart, minMeasurement, maxMeasurement), sort);
		ProductListResult result = productSearchService.search(condition, page, size);
		// 짧은 캐시와 ETag를 제공해 목록 반복 조회 비용을 낮춘다.
		return ResponseEntity.ok()
			.cacheControl(PRODUCT_LIST_CACHE_CONTROL)
			.eTag(weakEtag(result))
			.body(ApiResponse.success(result));
	}

	private static List<ProductSearchCondition.MeasurementFilter> parseMeasurementFilters(List<String> measurementFilters,
			String measurementPart, BigDecimal minMeasurement, BigDecimal maxMeasurement) {
		List<ProductSearchCondition.MeasurementFilter> filters = new ArrayList<>();
		if (measurementFilters != null) {
			// measurementFilters는 "부위:최솟값:최댓값" 형식을 여러 번 전달할 수 있다.
			for (String measurementFilter : measurementFilters) {
				filters.add(parseMeasurementFilter(measurementFilter));
			}
		}
		if (hasMeasurementFilter(measurementPart, minMeasurement, maxMeasurement)) {
			// 하위 호환용 단일 실측 필터 파라미터도 같은 리스트에 추가한다.
			filters.add(new ProductSearchCondition.MeasurementFilter(measurementPart, minMeasurement, maxMeasurement));
		}
		return filters;
	}

	private static ProductSearchCondition.MeasurementFilter parseMeasurementFilter(String measurementFilter) {
		if (!hasText(measurementFilter)) {
			throw new IllegalArgumentException("measurementFilters는 비어 있을 수 없습니다.");
		}
		// 빈 최솟값/최댓값을 허용하기 위해 split limit -1로 뒤쪽 빈 토큰을 보존한다.
		String[] tokens = measurementFilter.split(":", -1);
		if (tokens.length > 3) {
			throw new IllegalArgumentException("measurementFilters는 '부위:최솟값:최댓값' 형식이어야 합니다.");
		}
		String part = tokens[0].trim();
		if (!hasText(part)) {
			// 부위명 없이 숫자 범위만 있으면 어떤 실측값에 적용할지 알 수 없다.
			throw new IllegalArgumentException("measurementFilters의 부위명은 비어 있을 수 없습니다.");
		}
		BigDecimal min = tokens.length >= 2 ? parseMeasurementValue(tokens[1], measurementFilter) : null;
		BigDecimal max = tokens.length == 3 ? parseMeasurementValue(tokens[2], measurementFilter) : null;
		return new ProductSearchCondition.MeasurementFilter(part, min, max);
	}

	private static BigDecimal parseMeasurementValue(String value, String measurementFilter) {
		if (!hasText(value)) {
			// "허리::80"처럼 한쪽 범위만 지정하는 필터를 허용한다.
			return null;
		}
		try {
			return new BigDecimal(value.trim());
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("measurementFilters의 실측값은 숫자여야 합니다: " + measurementFilter);
		}
	}

	private static boolean hasMeasurementFilter(String part, BigDecimal minMeasurement, BigDecimal maxMeasurement) {
		return hasText(part) || minMeasurement != null || maxMeasurement != null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	@GetMapping("/{siteCode}/{sourceProductId}")
	ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(@PathVariable String siteCode,
			@PathVariable String sourceProductId) {
		// 서비스 결과를 API 응답 전용 레코드로 변환해 내부 DTO 변화가 외부 계약에 직접 새지 않게 한다.
		ProductDetailResponse result = ProductDetailResponse.from(productSearchService.getDetail(siteCode, sourceProductId));
		return ResponseEntity.ok()
			.cacheControl(PRODUCT_DETAIL_CACHE_CONTROL)
			.eTag(weakEtag(result))
			.body(ApiResponse.success(result));
	}

	private static String weakEtag(Object result) {
		// 응답 객체의 hashCode 기반 약한 ETag로 단순한 조건부 캐시를 제공한다.
		return "W/\"" + Integer.toHexString(result.hashCode()) + "\"";
	}

	record ProductDetailResponse(
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
		ProductAvailability stockStatus,
		Instant collectedAt,
		Instant lastSeenAt,
		Instant availabilityCheckedAt,
		List<MeasurementResponse> measurements
	) {

		private static ProductDetailResponse from(ProductDetailResult result) {
			return new ProductDetailResponse(
				result.id(),
				result.sourceProductId(),
				result.name(),
				result.originalPrice(),
				result.salePrice(),
				result.displayPrice(),
				result.description(),
				result.detailUrl(),
				result.thumbnailImageUrl(),
				result.siteCode(),
				result.siteName(),
				result.standardCategory(),
				result.standardSubCategory(),
				result.stockStatus(),
				result.collectedAt(),
				result.lastSeenAt(),
				result.availabilityCheckedAt(),
				result.measurements().stream().map(MeasurementResponse::from).toList()
			);
		}
	}

	record MeasurementResponse(
		String part,
		BigDecimal valueCm,
		Instant updatedAt
	) {

		private static MeasurementResponse from(ProductDetailResult.Measurement measurement) {
			return new MeasurementResponse(
				measurement.part(),
				measurement.valueCm(),
				measurement.updatedAt()
			);
		}
	}
}
