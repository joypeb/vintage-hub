package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import com.joypeb.vintagehub.product.persistence.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSearchService {

	private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
	private static final int MAX_PAGE_SIZE = 100;

	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;

	public ProductSearchService(ProductRepository productRepository, ProductMeasurementRepository measurementRepository) {
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
	}

	@Transactional(readOnly = true)
	public ProductListResult search(ProductSearchCondition condition, int page, int size) {
		int resolvedSize = normalizedSize(size);
		Pageable pageable = PageRequest.of(page, resolvedSize, condition.sort().sort());
		log.atDebug()
			.addKeyValue("event", "product.search.started")
			.addKeyValue("page", page)
			.addKeyValue("size", size)
			.addKeyValue("resolvedSize", resolvedSize)
			.addKeyValue("sort", condition.sort())
			.addKeyValue("keyword", condition.keyword())
			.addKeyValue("siteCode", condition.siteCode())
			.addKeyValue("standardCategory", condition.standardCategory())
			.addKeyValue("standardSubCategory", condition.standardSubCategory())
			.addKeyValue("stockStatus", condition.stockStatus())
			.addKeyValue("minPrice", condition.minPrice())
			.addKeyValue("maxPrice", condition.maxPrice())
			.addKeyValue("measurementFilters", condition.measurementFilters())
			.log("product.search.started");
		Page<ProductListItem> products = productRepository.findAll(ProductSpecifications.by(condition), pageable)
			.map(ProductListItem::from);
		log.atInfo()
			.addKeyValue("event", "product.search.completed")
			.addKeyValue("page", page)
			.addKeyValue("size", resolvedSize)
			.addKeyValue("sort", condition.sort())
			.addKeyValue("resultCount", products.getNumberOfElements())
			.addKeyValue("totalElements", products.getTotalElements())
			.log("product.search.completed");
		return ProductListResult.from(products);
	}

	@Transactional(readOnly = true)
	public ProductDetailResult getDetail(String siteCode, String sourceProductId) {
		log.atInfo()
			.addKeyValue("event", "product.detail.requested")
			.addKeyValue("siteCode", siteCode)
			.addKeyValue("sourceProductId", sourceProductId)
			.log("product.detail.requested");
		return productRepository.findBySiteCodeAndSourceProductId(siteCode, sourceProductId)
			.map(product -> {
				var measurements = measurementRepository.findByProductIdOrderByIdAsc(product.id());
				log.atInfo()
					.addKeyValue("event", "product.detail.completed")
					.addKeyValue("siteCode", siteCode)
					.addKeyValue("sourceProductId", sourceProductId)
					.addKeyValue("productId", product.id())
					.addKeyValue("measurementCount", measurements.size())
					.log("product.detail.completed");
				return ProductDetailResult.from(product, measurements);
			})
			.orElseThrow(() -> {
				log.atWarn()
					.addKeyValue("event", "product.detail.failed")
					.addKeyValue("siteCode", siteCode)
					.addKeyValue("sourceProductId", sourceProductId)
					.addKeyValue("reason", "not-found")
					.log("product.detail.failed");
				return new ResourceNotFoundException(
					"상품을 찾을 수 없습니다. siteCode=" + siteCode + ", sourceProductId=" + sourceProductId);
			});
	}

	private int normalizedSize(int size) {
		if (size < 1) {
			return 20;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}
}
