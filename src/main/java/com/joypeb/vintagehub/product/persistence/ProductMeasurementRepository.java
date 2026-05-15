package com.joypeb.vintagehub.product.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMeasurementRepository extends JpaRepository<ProductMeasurementEntity, Long> {

	void deleteByProduct(ProductEntity product);

	List<ProductMeasurementEntity> findByProductIdOrderByIdAsc(Long productId);
}
