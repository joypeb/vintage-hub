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
			// TTL이 꺼져 있으면 항상 DB에서 최신 필터 옵션을 읽는다.
			return loadFilterOptions();
		}
		CachedFilterOptions cached = cachedFilterOptions;
		Instant now = Instant.now();
		if (cached != null && cached.expiresAt().isAfter(now)) {
			// 짧은 TTL 캐시로 목록 화면 진입 때마다 집계 쿼리가 반복되는 것을 줄인다.
			return cached.result();
		}
		ProductFilterOptionsResult result = loadFilterOptions();
		// volatile 필드에 완성된 결과 객체를 교체해 락 없이 최신 캐시를 노출한다.
		cachedFilterOptions = new CachedFilterOptions(result, now.plus(filterOptionsCacheTtl));
		return result;
	}

	private ProductFilterOptionsResult loadFilterOptions() {
		// 서브카테고리는 상위 카테고리 응답 안에 중첩하기 위해 카테고리명으로 먼저 그룹화한다.
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
		// 사이트, 실측 부위, 정렬 옵션은 각각 독립적인 필터 그룹으로 내려준다.
		List<SiteOption> sites = productRepository.findSiteFilterOptions()
			.stream()
			.map(site -> new SiteOption(site.code(), site.name(), site.productCount()))
			.toList();
		List<MeasurementOption> measurements = measurementRepository.findMeasurementFilterOptions()
			.stream()
			.map(measurement -> new MeasurementOption(measurement.name(), measurement.productCount()))
			.toList();
		List<SortOption> sorts = Arrays.stream(ProductSortOption.values())
			// enum 정의 순서를 API 표시 순서로 사용한다.
			.map(sort -> new SortOption(sort.name(), sort.displayName()))
			.toList();
		return new ProductFilterOptionsResult(sites, categories, measurements, sorts);
	}

	private record CachedFilterOptions(ProductFilterOptionsResult result, Instant expiresAt) {
	}
}
