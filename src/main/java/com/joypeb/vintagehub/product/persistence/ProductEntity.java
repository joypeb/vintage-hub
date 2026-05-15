package com.joypeb.vintagehub.product.persistence;

import com.joypeb.vintagehub.crawl.domain.CrawledProductDetail;
import com.joypeb.vintagehub.crawl.domain.CrawledProductSummary;
import com.joypeb.vintagehub.crawl.domain.ProductAvailability;
import com.joypeb.vintagehub.crawl.persistence.CrawlSiteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "product", uniqueConstraints = {
	@UniqueConstraint(name = "uk_product_site_source", columnNames = {"site_id", "source_product_id"})
})
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "site_id", nullable = false)
	private CrawlSiteEntity site;

	@OneToMany(mappedBy = "product")
	private List<ProductMeasurementEntity> measurements = new ArrayList<>();

	@Column(name = "source_product_id", nullable = false)
	private String sourceProductId;

	@Column(nullable = false)
	private String name;

	private BigDecimal originalPrice;

	private BigDecimal salePrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductAvailability stockStatus;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false)
	private String detailUrl;

	private String thumbnailImageUrl;

	private String sourceCategoryName;

	private String standardCategory;

	private String standardSubCategory;

	private BigDecimal categoryConfidence;

	@Column(nullable = false)
	private Instant collectedAt;

	@Column(nullable = false)
	private Instant lastSeenAt;

	private Instant availabilityCheckedAt;

	private Instant availabilityNextCheckAt;

	protected ProductEntity() {
	}

	private ProductEntity(CrawlSiteEntity site, String sourceProductId) {
		this.site = site;
		this.sourceProductId = sourceProductId;
	}

	public static ProductEntity create(CrawlSiteEntity site, String sourceProductId) {
		return new ProductEntity(site, sourceProductId);
	}

	public void updateFrom(CrawledProductDetail detail, CrawledProductSummary summary, Instant collectedAt) {
		this.name = firstText(detail.name(), summary.name());
		this.originalPrice = detail.originalPrice();
		this.salePrice = detail.salePrice();
		this.stockStatus = detail.availability();
		this.description = detail.description();
		this.detailUrl = detail.ref().detailUrl().toString();
		this.thumbnailImageUrl = detail.thumbnailImageUrl() == null ? null : detail.thumbnailImageUrl().toString();
		this.sourceCategoryName = detail.sourceCategoryName();
		ProductCategoryMapping.ProductCategoryMappingResult categoryMapping = ProductCategoryMapping.from(site.code(),
			detail.sourceCategoryName(), this.name, detail.description());
		this.standardCategory = categoryMapping.standardCategory();
		this.standardSubCategory = categoryMapping.standardSubCategory();
		this.categoryConfidence = categoryMapping.categoryConfidence();
		this.collectedAt = collectedAt;
		this.lastSeenAt = collectedAt;
		this.availabilityCheckedAt = collectedAt;
		this.availabilityNextCheckAt = collectedAt;
	}

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

	public Long id() {
		return id;
	}

	public CrawlSiteEntity site() {
		return site;
	}

	public String sourceProductId() {
		return sourceProductId;
	}

	public String name() {
		return name;
	}

	public BigDecimal originalPrice() {
		return originalPrice;
	}

	public BigDecimal salePrice() {
		return salePrice;
	}

	public ProductAvailability stockStatus() {
		return stockStatus;
	}

	public String description() {
		return description;
	}

	public String detailUrl() {
		return detailUrl;
	}

	public String thumbnailImageUrl() {
		return thumbnailImageUrl;
	}

	public String standardCategory() {
		return standardCategory;
	}

	public String standardSubCategory() {
		return standardSubCategory;
	}

	public BigDecimal categoryConfidence() {
		return categoryConfidence;
	}

	public Instant collectedAt() {
		return collectedAt;
	}

	public Instant lastSeenAt() {
		return lastSeenAt;
	}

	public Instant availabilityCheckedAt() {
		return availabilityCheckedAt;
	}

	public Instant availabilityNextCheckAt() {
		return availabilityNextCheckAt;
	}

	public boolean needsCrawlRepair() {
		return isBlank(name) || originalPrice == null || isBlank(thumbnailImageUrl);
	}

	private String firstText(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
