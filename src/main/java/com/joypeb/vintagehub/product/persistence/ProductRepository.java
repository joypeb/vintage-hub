package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {

	Optional<ProductEntity> findBySiteAndSourceProductId(CrawlSiteEntity site, String sourceProductId);
}
