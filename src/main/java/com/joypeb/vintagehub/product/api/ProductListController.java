package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.application.ProductDetailResult;
import com.joypeb.vintagehub.product.application.ProductListResult;
import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import com.joypeb.vintagehub.product.application.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
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
			@RequestParam(required = false) String siteCode,
			@RequestParam(required = false) String standardCategory,
			@RequestParam(required = false) String standardSubCategory,
			@RequestParam(required = false) ProductAvailability stockStatus,
			@RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(required = false) String measurementPart,
			@RequestParam(required = false) BigDecimal minMeasurement,
			@RequestParam(required = false) BigDecimal maxMeasurement,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		ProductSearchCondition condition = new ProductSearchCondition(siteCode, standardCategory, standardSubCategory,
			stockStatus, minPrice, maxPrice, measurementPart, minMeasurement, maxMeasurement);
		return ApiResponse.success(productSearchService.search(condition, page, size));
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
