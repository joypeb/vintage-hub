package com.joypeb.vintagehub.product.application;

import com.joypeb.vintagehub.common.api.ResourceNotFoundException;
import com.joypeb.vintagehub.product.persistence.ProductMeasurementRepository;
import com.joypeb.vintagehub.product.persistence.ProductRepository;
import com.joypeb.vintagehub.product.persistence.ProductSpecifications;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSearchService {

	private static final int MAX_PAGE_SIZE = 100;

	private final ProductRepository productRepository;
	private final ProductMeasurementRepository measurementRepository;

	public ProductSearchService(ProductRepository productRepository, ProductMeasurementRepository measurementRepository) {
		this.productRepository = productRepository;
		this.measurementRepository = measurementRepository;
	}

	@Transactional(readOnly = true)
	public ProductListResult search(ProductSearchCondition condition, int page, int size) {
		Pageable pageable = PageRequest.of(page, normalizedSize(size), Sort.by(Sort.Direction.DESC, "collectedAt"));
		return ProductListResult.from(productRepository.findAll(ProductSpecifications.by(condition), pageable)
			.map(ProductListItem::from));
	}

	@Transactional(readOnly = true)
	public ProductDetailResult getDetail(String siteCode, String sourceProductId) {
		return productRepository.findBySiteCodeAndSourceProductId(siteCode, sourceProductId)
			.map(product -> ProductDetailResult.from(product,
				measurementRepository.findByProductIdOrderByIdAsc(product.id())))
			.orElseThrow(() -> new ResourceNotFoundException(
				"상품을 찾을 수 없습니다. siteCode=" + siteCode + ", sourceProductId=" + sourceProductId));
	}

	private int normalizedSize(int size) {
		if (size < 1) {
			return 20;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}
}
