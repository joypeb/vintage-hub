package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.application.ProductDetailResult;
import com.joypeb.vintagehub.product.application.ProductListResult;
import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import com.joypeb.vintagehub.product.application.ProductSearchService;
import com.joypeb.vintagehub.product.application.ProductSortOption;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
class ProductListController {

	private final ProductSearchService productSearchService;

	ProductListController(ProductSearchService productSearchService) {
		this.productSearchService = productSearchService;
	}

	@GetMapping
	ApiResponse<ProductListResult> listProducts(
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
		ProductSearchCondition condition = new ProductSearchCondition(keyword, siteCode, standardCategory, standardSubCategory,
			stockStatus, minPrice, maxPrice,
			parseMeasurementFilters(measurementFilters, measurementPart, minMeasurement, maxMeasurement), sort);
		return ApiResponse.success(productSearchService.search(condition, page, size));
	}

	private static List<ProductSearchCondition.MeasurementFilter> parseMeasurementFilters(List<String> measurementFilters,
			String measurementPart, BigDecimal minMeasurement, BigDecimal maxMeasurement) {
		List<ProductSearchCondition.MeasurementFilter> filters = new ArrayList<>();
		if (measurementFilters != null) {
			for (String measurementFilter : measurementFilters) {
				filters.add(parseMeasurementFilter(measurementFilter));
			}
		}
		if (hasMeasurementFilter(measurementPart, minMeasurement, maxMeasurement)) {
			filters.add(new ProductSearchCondition.MeasurementFilter(measurementPart, minMeasurement, maxMeasurement));
		}
		return filters;
	}

	private static ProductSearchCondition.MeasurementFilter parseMeasurementFilter(String measurementFilter) {
		if (!hasText(measurementFilter)) {
			throw new IllegalArgumentException("measurementFilters는 비어 있을 수 없습니다.");
		}
		String[] tokens = measurementFilter.split(":", -1);
		if (tokens.length > 3) {
			throw new IllegalArgumentException("measurementFilters는 '부위:최솟값:최댓값' 형식이어야 합니다.");
		}
		String part = tokens[0].trim();
		if (!hasText(part)) {
			throw new IllegalArgumentException("measurementFilters의 부위명은 비어 있을 수 없습니다.");
		}
		BigDecimal min = tokens.length >= 2 ? parseMeasurementValue(tokens[1], measurementFilter) : null;
		BigDecimal max = tokens.length == 3 ? parseMeasurementValue(tokens[2], measurementFilter) : null;
		return new ProductSearchCondition.MeasurementFilter(part, min, max);
	}

	private static BigDecimal parseMeasurementValue(String value, String measurementFilter) {
		if (!hasText(value)) {
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
	ApiResponse<ProductDetailResponse> getProductDetail(@PathVariable String siteCode,
			@PathVariable String sourceProductId) {
		return ApiResponse.success(ProductDetailResponse.from(productSearchService.getDetail(siteCode, sourceProductId)));
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
