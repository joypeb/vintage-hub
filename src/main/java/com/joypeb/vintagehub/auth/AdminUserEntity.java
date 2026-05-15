package com.joypeb.vintagehub.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_user")
class AdminUserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected AdminUserEntity() {
	}

	private AdminUserEntity(String username, String passwordHash, boolean enabled) {
		Instant now = Instant.now();
		this.username = username;
		this.passwordHash = passwordHash;
		this.enabled = enabled;
		this.createdAt = now;
		this.updatedAt = now;
	}

	static AdminUserEntity create(String username, String passwordHash, boolean enabled) {
		return new AdminUserEntity(username, passwordHash, enabled);
	}

	Long id() {
		return id;
	}

	String username() {
		return username;
	}

	String passwordHash() {
		return passwordHash;
	}

	boolean enabled() {
		return enabled;
	}
}
