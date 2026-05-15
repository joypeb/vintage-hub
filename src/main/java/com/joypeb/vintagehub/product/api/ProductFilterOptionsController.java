package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/filter-options")
class ProductFilterOptionsController {

	private final ProductFilterOptionsService productFilterOptionsService;

	ProductFilterOptionsController(ProductFilterOptionsService productFilterOptionsService) {
		this.productFilterOptionsService = productFilterOptionsService;
	}

	@GetMapping
	ApiResponse<ProductFilterOptionsResult> getFilterOptions() {
		return ApiResponse.success(productFilterOptionsService.getFilterOptions());
	}
}
