package com.joypeb.vintagehub.crawl.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CrawlRunRepository extends JpaRepository<CrawlRunEntity, Long> {

	boolean existsBySite_CodeAndStatusIn(String siteCode, Collection<CrawlRunStatus> statuses);

	List<CrawlRunEntity> findByStatusInOrderByStartedAtDesc(Collection<CrawlRunStatus> statuses);
}
