package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

	Optional<ProductEntity> findBySiteAndSourceProductId(CrawlSiteEntity site, String sourceProductId);

	Optional<ProductEntity> findBySiteCodeAndSourceProductId(String siteCode, String sourceProductId);

	@Query("""
		select p
		from ProductEntity p
		join fetch p.site
		where p.availabilityNextCheckAt is not null
		  and p.availabilityNextCheckAt <= :now
		order by p.availabilityNextCheckAt asc, p.id asc
		""")
	List<ProductEntity> findDueForAvailabilityCheck(@Param("now") Instant now, Pageable pageable);
}
