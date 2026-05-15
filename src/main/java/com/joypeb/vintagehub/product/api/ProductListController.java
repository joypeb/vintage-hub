package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.product.application.ProductListResult;
import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import com.joypeb.vintagehub.product.application.ProductSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

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
}
