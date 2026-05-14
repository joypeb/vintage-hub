package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

	Optional<ProductEntity> findBySiteAndSourceProductId(CrawlSiteEntity site, String sourceProductId);
}
