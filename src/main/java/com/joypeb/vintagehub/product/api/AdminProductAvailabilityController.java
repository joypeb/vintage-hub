package com.joypeb.vintagehub.product.api;

import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.product.application.ProductAvailabilityCheckResult;
import com.joypeb.vintagehub.product.application.ProductAvailabilityCheckService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
class AdminProductAvailabilityController {

	private final ProductAvailabilityCheckService availabilityCheckService;

	AdminProductAvailabilityController(ProductAvailabilityCheckService availabilityCheckService) {
		this.availabilityCheckService = availabilityCheckService;
	}

	@PostMapping("/{productId}/availability-check")
	ResponseEntity<ApiResponse<ProductAvailabilityCheckResponse>> checkAvailability(@PathVariable Long productId) {
		ProductAvailabilityCheckResult result = availabilityCheckService.checkProduct(productId);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(ProductAvailabilityCheckResponse.from(result)));
	}

	record ProductAvailabilityCheckResponse(int checkedCount, int availableCount, int soldOutCount, int unknownCount,
			int failedCount) {

		private static ProductAvailabilityCheckResponse from(ProductAvailabilityCheckResult result) {
			return new ProductAvailabilityCheckResponse(result.checkedCount(), result.availableCount(),
				result.soldOutCount(), result.unknownCount(), result.failedCount());
		}
	}
}
