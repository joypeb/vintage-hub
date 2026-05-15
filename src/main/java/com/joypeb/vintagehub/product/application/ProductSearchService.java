package com.joypeb.vintagehub.product.application;

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

	public ProductSearchService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional(readOnly = true)
	public ProductListResult search(ProductSearchCondition condition, int page, int size) {
		Pageable pageable = PageRequest.of(page, normalizedSize(size), Sort.by(Sort.Direction.DESC, "collectedAt"));
		return ProductListResult.from(productRepository.findAll(ProductSpecifications.by(condition), pageable)
			.map(ProductListItem::from));
	}

	private int normalizedSize(int size) {
		if (size < 1) {
			return 20;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}
}
