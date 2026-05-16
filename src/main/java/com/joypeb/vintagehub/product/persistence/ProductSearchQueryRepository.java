package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.product.application.ProductListItem;
import com.joypeb.vintagehub.product.application.ProductSearchCondition;
import com.joypeb.vintagehub.product.application.ProductSearchCondition.MeasurementFilter;
import com.joypeb.vintagehub.product.application.ProductSortOption;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ProductSearchQueryRepository {

	@PersistenceContext
	private EntityManager entityManager;

	public Page<ProductListItem> search(ProductSearchCondition condition, int page, int size) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<ProductListItem> query = criteriaBuilder.createQuery(ProductListItem.class);
		Root<ProductEntity> product = query.from(ProductEntity.class);
		Join<ProductEntity, ?> site = product.join("site", JoinType.INNER);
		Expression<BigDecimal> displayPrice = displayPrice(criteriaBuilder, product);

		query.select(criteriaBuilder.construct(ProductListItem.class,
			product.get("id"),
			product.get("sourceProductId"),
			product.get("name"),
			product.get("originalPrice"),
			product.get("salePrice"),
			displayPrice,
			product.get("detailUrl"),
			product.get("thumbnailImageUrl"),
			site.get("code"),
			site.get("displayName"),
			product.get("standardCategory"),
			product.get("standardSubCategory"),
			product.get("stockStatus"),
			product.get("collectedAt")
		));
		query.where(predicates(condition, product, criteriaBuilder).toArray(Predicate[]::new));
		query.orderBy(orders(condition.sort(), product, displayPrice, criteriaBuilder));
		if (hasMeasurementFilter(condition)) {
			query.distinct(true);
		}

		TypedQuery<ProductListItem> typedQuery = entityManager.createQuery(query)
			.setFirstResult(Math.max(page, 0) * size)
			.setMaxResults(size);
		List<ProductListItem> content = typedQuery.getResultList();
		long total = count(condition, criteriaBuilder);
		return new PageImpl<>(content, PageRequest.of(Math.max(page, 0), size), total);
	}

	private long count(ProductSearchCondition condition, CriteriaBuilder criteriaBuilder) {
		CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
		Root<ProductEntity> product = countQuery.from(ProductEntity.class);
		countQuery.where(predicates(condition, product, criteriaBuilder).toArray(Predicate[]::new));
		if (hasMeasurementFilter(condition)) {
			countQuery.select(criteriaBuilder.countDistinct(product.get("id")));
		}
		else {
			countQuery.select(criteriaBuilder.count(product));
		}
		return entityManager.createQuery(countQuery).getSingleResult();
	}

	private List<Predicate> predicates(ProductSearchCondition condition, Root<ProductEntity> product,
			CriteriaBuilder criteriaBuilder) {
		List<Predicate> predicates = new ArrayList<>();
		addKeywordPredicates(condition.keyword(), product.get("name"), criteriaBuilder, predicates);
		if (hasText(condition.siteCode())) {
			predicates.add(criteriaBuilder.equal(product.get("site").get("code"), condition.siteCode()));
		}
		if (hasText(condition.standardCategory())) {
			predicates.add(criteriaBuilder.equal(product.get("standardCategory"), condition.standardCategory()));
		}
		if (hasText(condition.standardSubCategory())) {
			predicates.add(criteriaBuilder.equal(product.get("standardSubCategory"), condition.standardSubCategory()));
		}
		if (condition.stockStatus() != null) {
			predicates.add(criteriaBuilder.equal(product.get("stockStatus"), condition.stockStatus()));
		}
		addPricePredicates(condition, displayPrice(criteriaBuilder, product), criteriaBuilder, predicates);
		addMeasurementPredicates(condition, product, criteriaBuilder, predicates);
		return predicates;
	}

	private void addKeywordPredicates(String keyword, Path<String> name, CriteriaBuilder criteriaBuilder,
			List<Predicate> predicates) {
		if (!hasText(keyword)) {
			return;
		}
		String[] tokens = keyword.trim().toLowerCase(Locale.ROOT).split("\\s+");
		for (String token : tokens) {
			if (hasText(token)) {
				predicates.add(criteriaBuilder.like(criteriaBuilder.lower(name),
					"%" + escapeLikePattern(token) + "%", '\\'));
			}
		}
	}

	private String escapeLikePattern(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
	}

	private void addPricePredicates(ProductSearchCondition condition, Expression<BigDecimal> displayPrice,
			CriteriaBuilder criteriaBuilder, List<Predicate> predicates) {
		if (condition.minPrice() != null) {
			predicates.add(criteriaBuilder.greaterThanOrEqualTo(displayPrice, condition.minPrice()));
		}
		if (condition.maxPrice() != null) {
			predicates.add(criteriaBuilder.lessThanOrEqualTo(displayPrice, condition.maxPrice()));
		}
	}

	private void addMeasurementPredicates(ProductSearchCondition condition, Root<ProductEntity> product,
			CriteriaBuilder criteriaBuilder, List<Predicate> predicates) {
		if (!hasMeasurementFilter(condition)) {
			return;
		}
		for (MeasurementFilter filter : condition.measurementFilters()) {
			if (!hasMeasurementFilter(filter)) {
				continue;
			}
			Join<ProductEntity, ProductMeasurementEntity> measurement = product.join("measurements", JoinType.INNER);
			if (hasText(filter.part())) {
				predicates.add(criteriaBuilder.equal(measurement.get("part"), filter.part()));
			}
			if (filter.minMeasurement() != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(measurement.get("valueCm"), filter.minMeasurement()));
			}
			if (filter.maxMeasurement() != null) {
				predicates.add(criteriaBuilder.lessThanOrEqualTo(measurement.get("valueCm"), filter.maxMeasurement()));
			}
		}
	}

	private List<Order> orders(ProductSortOption sort, Root<ProductEntity> product, Expression<BigDecimal> displayPrice,
			CriteriaBuilder criteriaBuilder) {
		return switch (sort) {
			case PRICE_LOW -> List.of(
				criteriaBuilder.asc(criteriaBuilder.selectCase().when(criteriaBuilder.isNull(displayPrice), 1).otherwise(0)),
				criteriaBuilder.asc(displayPrice),
				criteriaBuilder.desc(product.get("collectedAt")),
				criteriaBuilder.desc(product.get("id"))
			);
			case PRICE_HIGH -> List.of(
				criteriaBuilder.asc(criteriaBuilder.selectCase().when(criteriaBuilder.isNull(displayPrice), 1).otherwise(0)),
				criteriaBuilder.desc(displayPrice),
				criteriaBuilder.desc(product.get("collectedAt")),
				criteriaBuilder.desc(product.get("id"))
			);
			case LATEST -> List.of(
				criteriaBuilder.desc(product.get("collectedAt")),
				criteriaBuilder.desc(product.get("id"))
			);
		};
	}

	private Expression<BigDecimal> displayPrice(CriteriaBuilder criteriaBuilder, Root<ProductEntity> product) {
		return criteriaBuilder.coalesce(product.get("salePrice"), product.get("originalPrice"));
	}

	private boolean hasMeasurementFilter(ProductSearchCondition condition) {
		return condition.measurementFilters().stream().anyMatch(this::hasMeasurementFilter);
	}

	private boolean hasMeasurementFilter(MeasurementFilter filter) {
		return hasText(filter.part()) || filter.minMeasurement() != null || filter.maxMeasurement() != null;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
