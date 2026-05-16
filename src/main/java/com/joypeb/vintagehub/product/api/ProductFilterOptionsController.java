package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/products/filter-options")
class ProductFilterOptionsController {

	private static final CacheControl FILTER_OPTIONS_CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(60))
		.mustRevalidate();

	private final ProductFilterOptionsService productFilterOptionsService;

	ProductFilterOptionsController(ProductFilterOptionsService productFilterOptionsService) {
		this.productFilterOptionsService = productFilterOptionsService;
	}

	@GetMapping
	ResponseEntity<ApiResponse<ProductFilterOptionsResult>> getFilterOptions() {
		ProductFilterOptionsResult result = productFilterOptionsService.getFilterOptions();
		return ResponseEntity.ok()
			.cacheControl(FILTER_OPTIONS_CACHE_CONTROL)
			.eTag(weakEtag(result))
			.body(ApiResponse.success(result));
	}

	private static String weakEtag(ProductFilterOptionsResult result) {
		return "W/\"" + Integer.toHexString(result.hashCode()) + "\"";
	}
}
