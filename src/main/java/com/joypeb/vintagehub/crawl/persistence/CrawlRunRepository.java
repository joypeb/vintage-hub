package com.joypeb.vintagehub.crawl.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CrawlRunRepository extends JpaRepository<CrawlRunEntity, Long> {
}
