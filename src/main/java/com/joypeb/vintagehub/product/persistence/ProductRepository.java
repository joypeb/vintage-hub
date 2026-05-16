package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import com.joypeb.vintagehub.product.application.FilterOptionCount;
import com.joypeb.vintagehub.product.application.ProductSiteFilterOption;
import com.joypeb.vintagehub.product.application.ProductSubCategoryFilterOption;
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

	@Query("""
		select p
		from ProductEntity p
		join fetch p.site
		where p.site.code = :siteCode
		  and p.sourceProductId = :sourceProductId
		""")
	Optional<ProductEntity> findBySiteCodeAndSourceProductId(@Param("siteCode") String siteCode,
			@Param("sourceProductId") String sourceProductId);

	@Query("""
		select new com.joypeb.vintagehub.product.application.ProductSiteFilterOption(
			p.site.code,
			p.site.displayName,
			count(p.id)
		)
		from ProductEntity p
		group by p.site.code, p.site.displayName
		order by count(p.id) desc, p.site.code asc
		""")
	List<ProductSiteFilterOption> findSiteFilterOptions();

	@Query("""
		select new com.joypeb.vintagehub.product.application.FilterOptionCount(
			p.standardCategory,
			count(p.id)
		)
		from ProductEntity p
		where p.standardCategory is not null
		  and trim(p.standardCategory) <> ''
		group by p.standardCategory
		order by count(p.id) desc, p.standardCategory asc
		""")
	List<FilterOptionCount> findCategoryFilterOptions();

	@Query("""
		select new com.joypeb.vintagehub.product.application.ProductSubCategoryFilterOption(
			p.standardCategory,
			p.standardSubCategory,
			count(p.id)
		)
		from ProductEntity p
		where p.standardCategory is not null
		  and trim(p.standardCategory) <> ''
		  and p.standardSubCategory is not null
		  and trim(p.standardSubCategory) <> ''
		group by p.standardCategory, p.standardSubCategory
		order by p.standardCategory asc, count(p.id) desc, p.standardSubCategory asc
		""")
	List<ProductSubCategoryFilterOption> findSubCategoryFilterOptions();

	@Query("""
		select distinct p.site.code
		from ProductEntity p
		where p.availabilityNextCheckAt is not null
		  and p.availabilityNextCheckAt <= :now
		order by p.site.code asc
		""")
	List<String> findDueSiteCodesForAvailabilityCheck(@Param("now") Instant now);

	@Query("""
		select p
		from ProductEntity p
		join fetch p.site
		where p.site.code = :siteCode
		  and p.availabilityNextCheckAt is not null
		  and p.availabilityNextCheckAt <= :now
		order by p.availabilityNextCheckAt asc, p.id asc
		""")
	List<ProductEntity> findDueForAvailabilityCheckBySiteCode(@Param("siteCode") String siteCode,
			@Param("now") Instant now, Pageable pageable);
}
