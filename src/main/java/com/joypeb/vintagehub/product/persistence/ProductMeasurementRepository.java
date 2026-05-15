package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.product.application.FilterOptionCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductMeasurementRepository extends JpaRepository<ProductMeasurementEntity, Long> {

	void deleteByProduct(ProductEntity product);

	List<ProductMeasurementEntity> findByProductIdOrderByIdAsc(Long productId);

	@Query("""
		select new com.joypeb.vintagehub.product.application.FilterOptionCount(
			m.part,
			count(distinct m.product.id)
		)
		from ProductMeasurementEntity m
		where m.part is not null
		  and trim(m.part) <> ''
		group by m.part
		order by m.part asc
		""")
	List<FilterOptionCount> findMeasurementFilterOptions();
}
