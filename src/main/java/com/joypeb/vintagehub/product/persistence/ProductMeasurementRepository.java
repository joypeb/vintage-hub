package com.joypeb.vintagehub.product.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMeasurementRepository extends JpaRepository<ProductMeasurementEntity, Long> {

	void deleteByProduct(ProductEntity product);
}
