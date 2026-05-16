package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.CategoryOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.MeasurementOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.SiteOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.SortOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.SubCategoryOption;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductFilterOptionsService {

	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;
	private final Duration filterOptionsCacheTtl;
	private volatile CachedFilterOptions cachedFilterOptions;

	public ProductFilterOptionsService(ProductRepository productRepository,
			ProductMeasurementRepository measurementRepository,
			@Value("${vintage-hub.product.filter-options.cache-ttl:60s}") Duration filterOptionsCacheTtl) {
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
		this.filterOptionsCacheTtl = filterOptionsCacheTtl;
	}

	@Transactional(readOnly = true)
	public ProductFilterOptionsResult getFilterOptions() {
		if (filterOptionsCacheTtl.isZero() || filterOptionsCacheTtl.isNegative()) {
			return loadFilterOptions();
		}
		CachedFilterOptions cached = cachedFilterOptions;
		Instant now = Instant.now();
		if (cached != null && cached.expiresAt().isAfter(now)) {
			return cached.result();
		}
		ProductFilterOptionsResult result = loadFilterOptions();
		cachedFilterOptions = new CachedFilterOptions(result, now.plus(filterOptionsCacheTtl));
		return result;
	}

	private ProductFilterOptionsResult loadFilterOptions() {
		Map<String, List<SubCategoryOption>> subCategoriesByCategory = productRepository.findSubCategoryFilterOptions()
			.stream()
			.collect(Collectors.groupingBy(ProductSubCategoryFilterOption::categoryName,
				Collectors.mapping(option -> new SubCategoryOption(option.name(), option.productCount()),
					Collectors.toList())));
		List<CategoryOption> categories = productRepository.findCategoryFilterOptions()
			.stream()
			.map(category -> new CategoryOption(category.name(), category.productCount(),
				subCategoriesByCategory.getOrDefault(category.name(), List.of())))
			.toList();
		List<SiteOption> sites = productRepository.findSiteFilterOptions()
			.stream()
			.map(site -> new SiteOption(site.code(), site.name(), site.productCount()))
			.toList();
		List<MeasurementOption> measurements = measurementRepository.findMeasurementFilterOptions()
			.stream()
			.map(measurement -> new MeasurementOption(measurement.name(), measurement.productCount()))
			.toList();
		List<SortOption> sorts = Arrays.stream(ProductSortOption.values())
			.map(sort -> new SortOption(sort.name(), sort.displayName()))
			.toList();
		return new ProductFilterOptionsResult(sites, categories, measurements, sorts);
	}

	private record CachedFilterOptions(ProductFilterOptionsResult result, Instant expiresAt) {
	}
}
