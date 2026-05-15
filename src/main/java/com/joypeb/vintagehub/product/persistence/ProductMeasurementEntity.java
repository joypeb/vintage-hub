package com.joypeb.vintagehub.product.persistence;

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
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_measurement")
public class ProductMeasurementEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductEntity product;

	@Column(nullable = false)
	private String part;

	@Column(nullable = false)
	private BigDecimal valueCm;

	@Column(columnDefinition = "text")
	private String rawText;

	@Column(nullable = false)
	private BigDecimal confidence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MeasurementSource source;

	@Column(nullable = false)
	private Instant updatedAt;

	protected ProductMeasurementEntity() {
	}

	private ProductMeasurementEntity(ProductEntity product, String part, BigDecimal valueCm, String rawText) {
		this.product = product;
		this.part = part;
		this.valueCm = valueCm;
		this.rawText = rawText;
		this.confidence = new BigDecimal("0.80");
		this.source = MeasurementSource.AUTO;
		this.updatedAt = Instant.now();
	}

	public static ProductMeasurementEntity automatic(ProductEntity product, String part, BigDecimal valueCm,
			String rawText) {
		return new ProductMeasurementEntity(product, part, valueCm, rawText);
	}

	public String part() {
		return part;
	}

	public BigDecimal valueCm() {
		return valueCm;
	}

	public String rawText() {
		return rawText;
	}

	public BigDecimal confidence() {
		return confidence;
	}

	public MeasurementSource source() {
		return source;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
