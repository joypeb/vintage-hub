package com.joypeb.vintagehub.crawl.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CrawlSiteRepository extends JpaRepository<CrawlSiteEntity, Long> {

	Optional<CrawlSiteEntity> findByCode(String code);
}
