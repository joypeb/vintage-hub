package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.CategoryOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.MeasurementOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.SiteOption;
import com.joypeb.vintagehub.product.application.ProductFilterOptionsResult.SubCategoryOption;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductFilterOptionsService {

	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;

	public ProductFilterOptionsService(ProductRepository productRepository,
			ProductMeasurementRepository measurementRepository) {
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
	}

	@Transactional(readOnly = true)
	public ProductFilterOptionsResult getFilterOptions() {
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
		return new ProductFilterOptionsResult(sites, categories, measurements);
	}
}
