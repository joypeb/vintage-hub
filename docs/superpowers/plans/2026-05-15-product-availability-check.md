# Product Availability Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator-triggered and scheduled product availability checks using `availability_next_check_at` so large `AVAILABLE`/`SOLD_OUT` product sets do not require full scans.

**Architecture:** Keep this inside the existing modular monolith. Add a focused availability service that selects due products by `availability_next_check_at`, calls the existing `SiteCrawler.checkAvailability(...)`, updates product status and next check time, and is used by both admin API and scheduler.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JPA, Flyway, JUnit 5, Mockito, MockMvc.

---

## File Structure

- Create `src/main/resources/db/migration/V3__add_product_availability_next_check_at.sql`
  - Add `availability_next_check_at`.
  - Add an index for due-product lookup.
- Modify `src/main/java/com/joypeb/vintagehub/product/persistence/ProductEntity.java`
  - Store `availabilityNextCheckAt`.
  - Add methods for availability check success/failure.
  - Initialize next check time during normal crawl upsert.
- Modify `src/main/java/com/joypeb/vintagehub/product/persistence/ProductRepository.java`
  - Add due-product query ordered by `availabilityNextCheckAt`.
- Create `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckProperties.java`
  - Bind batch size, request delay, fixed rate, and status TTL settings.
- Create `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckResult.java`
  - Return compact result counts for API and logs.
- Create `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckService.java`
  - Own all manual and batch availability checking behavior.
- Create `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckScheduler.java`
  - Run scheduled due-product checks.
- Create `src/main/java/com/joypeb/vintagehub/product/api/AdminProductAvailabilityController.java`
  - Add admin manual check endpoint for one product.
- Modify `src/main/resources/application.yml`
  - Add conservative default availability check settings.
- Modify `docs/internal-api.md`
  - Document the new admin API and scheduler settings.
- Add tests:
  - `src/test/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckServiceTest.java`
  - `src/test/java/com/joypeb/vintagehub/product/api/AdminProductAvailabilityControllerTest.java`

---

## Task 1: Schema and Entity Support

**Files:**
- Create: `src/main/resources/db/migration/V3__add_product_availability_next_check_at.sql`
- Modify: `src/main/java/com/joypeb/vintagehub/product/persistence/ProductEntity.java`

- [ ] Create the migration:

```sql
alter table product
	add column availability_next_check_at timestamptz;

update product
set availability_next_check_at = coalesce(availability_checked_at, collected_at);

create index idx_product_availability_next_check_at on product(availability_next_check_at);
```

- [ ] Add `private Instant availabilityNextCheckAt;` and getter `availabilityNextCheckAt()` to `ProductEntity`.

- [ ] Add these entity methods:

```java
public void updateFrom(CrawledProductDetail detail, CrawledProductSummary summary, Instant collectedAt,
		Instant nextAvailabilityCheckAt) {
	updateFrom(detail, summary, collectedAt);
	this.availabilityNextCheckAt = nextAvailabilityCheckAt;
}

public void markAvailabilityCheckSucceeded(ProductAvailability availability, Instant checkedAt,
		Instant nextCheckAt) {
	this.stockStatus = availability;
	this.availabilityCheckedAt = checkedAt;
	this.availabilityNextCheckAt = nextCheckAt;
}

public void markAvailabilityCheckFailed(Instant checkedAt, Instant nextCheckAt) {
	this.stockStatus = ProductAvailability.CHECK_FAILED;
	this.availabilityCheckedAt = checkedAt;
	this.availabilityNextCheckAt = nextCheckAt;
}
```

- [ ] Keep the existing `updateFrom(detail, summary, collectedAt)` method for current callers, but make it set a non-null default next check time:

```java
this.availabilityNextCheckAt = collectedAt;
```

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.product.persistence.ProductCategoryMappingTest
```

Expected: existing product persistence-adjacent tests still pass.

---

## Task 2: Availability Check Configuration

**Files:**
- Create: `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] Create configuration properties:

```java
package com.joypeb.vintagehub.product.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "vintage-hub.product.availability-check")
public record ProductAvailabilityCheckProperties(
	boolean enabled,
	int batchSize,
	Duration requestDelay,
	Duration availableTtl,
	Duration soldOutTtl,
	Duration unknownTtl,
	Duration checkFailedTtl
) {
	public ProductAvailabilityCheckProperties {
		if (batchSize < 1) {
			batchSize = 20;
		}
	}
}
```

- [ ] Add `@EnableConfigurationProperties(ProductAvailabilityCheckProperties.class)` to a configuration class or make the properties class a Spring component if that matches the local style better.

- [ ] Add defaults to `application.yml`:

```yaml
vintage-hub:
  product:
    availability-check:
      enabled: true
      fixed-rate: 10m
      batch-size: 20
      request-delay: 1s
      available-ttl: 6h
      sold-out-ttl: 7d
      unknown-ttl: 1h
      check-failed-ttl: 2h
```

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.VintageHubBeApplicationTests
```

Expected: Spring context loads.

---

## Task 3: Due Product Query

**Files:**
- Modify: `src/main/java/com/joypeb/vintagehub/product/persistence/ProductRepository.java`

- [ ] Add a pageable query for products due at or before a timestamp:

```java
@Query("""
	select p
	from ProductEntity p
	join fetch p.site
	where p.availabilityNextCheckAt is not null
	  and p.availabilityNextCheckAt <= :now
	order by p.availabilityNextCheckAt asc, p.id asc
	""")
List<ProductEntity> findDueForAvailabilityCheck(@Param("now") Instant now, Pageable pageable);
```

- [ ] Add required imports: `Pageable`, `Query`, `Param`, `Instant`, `List`.

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.product.api.ProductListControllerTest
```

Expected: product query changes do not break existing product API tests.

---

## Task 4: Availability Check Service

**Files:**
- Create: `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckResult.java`
- Create: `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckService.java`
- Test: `src/test/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckServiceTest.java`

- [ ] Create result record:

```java
package com.joypeb.vintagehub.product.application;

public record ProductAvailabilityCheckResult(
	int checkedCount,
	int availableCount,
	int soldOutCount,
	int unknownCount,
	int failedCount
) {
}
```

- [ ] Write tests for:
  - Manual product check updates status and next check time.
  - Due batch checks only repository-selected products.
  - Crawler exception marks product as `CHECK_FAILED`.
  - TTL is `6h` for `AVAILABLE`, `7d` for `SOLD_OUT`, `1h` for `UNKNOWN`, `2h` for `CHECK_FAILED`.

- [ ] Implement `ProductAvailabilityCheckService` with:
  - `checkProduct(Long productId)`
  - `checkDueProducts()`
  - `checkDueProducts(int batchSize)`
  - internal `nextCheckAt(ProductAvailability status, Instant checkedAt)`
  - request delay between products, reusing the simple sleeper pattern from `CrawlRunService`

- [ ] For manual product lookup, throw `ResourceNotFoundException` when the product ID does not exist.

- [ ] Build `CrawledProductRef` from the stored source product id and detail URL:

```java
new CrawledProductRef(product.sourceProductId(), URI.create(product.detailUrl()))
```

- [ ] Resolve crawler with `crawlerRegistry.requireBySiteCode(product.site().code())`.

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.product.application.ProductAvailabilityCheckServiceTest
```

Expected: new service tests pass.

---

## Task 5: Admin Manual API

**Files:**
- Create: `src/main/java/com/joypeb/vintagehub/product/api/AdminProductAvailabilityController.java`
- Test: `src/test/java/com/joypeb/vintagehub/product/api/AdminProductAvailabilityControllerTest.java`

- [ ] Add endpoint:

```http
POST /api/admin/products/{productId}/availability-check
```

- [ ] Response body:

```json
{
  "success": true,
  "data": {
    "checkedCount": 1,
    "availableCount": 1,
    "soldOutCount": 0,
    "unknownCount": 0,
    "failedCount": 0
  }
}
```

- [ ] Controller returns `202 Accepted` to match the existing crawl-run request style.

- [ ] Test success and not-found error response with `MockMvc`.

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.product.api.AdminProductAvailabilityControllerTest
```

Expected: new API tests pass.

---

## Task 6: Scheduler

**Files:**
- Create: `src/main/java/com/joypeb/vintagehub/product/application/ProductAvailabilityCheckScheduler.java`

- [ ] Add scheduler:

```java
@Component
class ProductAvailabilityCheckScheduler {

	private static final Logger log = LoggerFactory.getLogger(ProductAvailabilityCheckScheduler.class);

	private final ProductAvailabilityCheckService service;
	private final ProductAvailabilityCheckProperties properties;

	ProductAvailabilityCheckScheduler(ProductAvailabilityCheckService service,
			ProductAvailabilityCheckProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Scheduled(fixedRateString = "${vintage-hub.product.availability-check.fixed-rate:10m}")
	void checkDueProducts() {
		if (!properties.enabled()) {
			return;
		}
		ProductAvailabilityCheckResult result = service.checkDueProducts();
		log.info("Product availability batch finished: checked={} available={} soldOut={} unknown={} failed={}",
			result.checkedCount(), result.availableCount(), result.soldOutCount(), result.unknownCount(),
			result.failedCount());
	}
}
```

- [ ] Ensure scheduling is enabled. If `@EnableScheduling` is absent, add it to `VintageHubBeApplication`.

- [ ] Run:

```bash
./gradlew test --tests com.joypeb.vintagehub.VintageHubBeApplicationTests
```

Expected: scheduler bean loads with default properties.

---

## Task 7: Documentation and Full Verification

**Files:**
- Modify: `docs/internal-api.md`

- [ ] Document `POST /api/admin/products/{productId}/availability-check`.

- [ ] Document settings:
  - `vintage-hub.product.availability-check.enabled`
  - `vintage-hub.product.availability-check.fixed-rate`
  - `vintage-hub.product.availability-check.batch-size`
  - `vintage-hub.product.availability-check.request-delay`
  - `vintage-hub.product.availability-check.available-ttl`
  - `vintage-hub.product.availability-check.sold-out-ttl`
  - `vintage-hub.product.availability-check.unknown-ttl`
  - `vintage-hub.product.availability-check.check-failed-ttl`

- [ ] Run focused tests:

```bash
./gradlew test --tests com.joypeb.vintagehub.product.application.ProductAvailabilityCheckServiceTest --tests com.joypeb.vintagehub.product.api.AdminProductAvailabilityControllerTest
```

Expected: new tests pass.

- [ ] Run full tests:

```bash
./gradlew test
```

Expected: all tests pass.

---

## Self-Review

- Spec coverage: Covers A batch and C manual admin API. B detail-view trigger is intentionally excluded from this implementation.
- Scale concern: `availability_next_check_at` plus index prevents repeated scans over all `AVAILABLE`/`SOLD_OUT` products.
- Scope: No history table, queue, distributed lock, or retry framework in this phase.
- Ambiguity resolved: Failed checks write `CHECK_FAILED` and schedule the next attempt using `check-failed-ttl`.
