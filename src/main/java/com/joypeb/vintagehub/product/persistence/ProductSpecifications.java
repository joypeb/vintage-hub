package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<ProductEntity> by(ProductSearchCondition condition) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (hasText(condition.siteCode())) {
				predicates.add(criteriaBuilder.equal(root.get("site").get("code"), condition.siteCode()));
			}
			if (hasText(condition.standardCategory())) {
				predicates.add(criteriaBuilder.equal(root.get("standardCategory"), condition.standardCategory()));
			}
			if (hasText(condition.standardSubCategory())) {
				predicates.add(criteriaBuilder.equal(root.get("standardSubCategory"), condition.standardSubCategory()));
			}
			if (condition.stockStatus() != null) {
				predicates.add(criteriaBuilder.equal(root.get("stockStatus"), condition.stockStatus()));
			}
			addPricePredicates(condition, root.get("salePrice"), root.get("originalPrice"), criteriaBuilder, predicates);
			addMeasurementPredicates(condition, root, criteriaBuilder, predicates);
			if (query != null) {
				query.distinct(true);
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static void addPricePredicates(ProductSearchCondition condition,
			jakarta.persistence.criteria.Path<BigDecimal> salePrice,
			jakarta.persistence.criteria.Path<BigDecimal> originalPrice,
			CriteriaBuilder criteriaBuilder,
			List<Predicate> predicates) {
		CriteriaBuilder.Coalesce<BigDecimal> displayPrice = criteriaBuilder.coalesce();
		displayPrice.value(salePrice);
		displayPrice.value(originalPrice);
		if (condition.minPrice() != null) {
			predicates.add(criteriaBuilder.greaterThanOrEqualTo(displayPrice, condition.minPrice()));
		}
		if (condition.maxPrice() != null) {
			predicates.add(criteriaBuilder.lessThanOrEqualTo(displayPrice, condition.maxPrice()));
		}
	}

	private static void addMeasurementPredicates(ProductSearchCondition condition,
			jakarta.persistence.criteria.Root<ProductEntity> root,
			CriteriaBuilder criteriaBuilder,
			List<Predicate> predicates) {
		if (!hasMeasurementFilter(condition)) {
			return;
		}
		Join<ProductEntity, ProductMeasurementEntity> measurement = root.join("measurements");
		if (hasText(condition.measurementPart())) {
			predicates.add(criteriaBuilder.equal(measurement.get("part"), condition.measurementPart()));
		}
		if (condition.minMeasurement() != null) {
			predicates.add(criteriaBuilder.greaterThanOrEqualTo(measurement.get("valueCm"), condition.minMeasurement()));
		}
		if (condition.maxMeasurement() != null) {
			predicates.add(criteriaBuilder.lessThanOrEqualTo(measurement.get("valueCm"), condition.maxMeasurement()));
		}
	}

	private static boolean hasMeasurementFilter(ProductSearchCondition condition) {
		return hasText(condition.measurementPart()) || condition.minMeasurement() != null
			|| condition.maxMeasurement() != null;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
