package com.joypeb.vintagehub.product.application;

public record ProductAvailabilityCheckResult(
	int checkedCount,
	int availableCount,
	int soldOutCount,
	int unknownCount,
	int failedCount
) {
}
