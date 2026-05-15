package com.joypeb.vintagehub.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {

	Optional<AdminUserEntity> findByUsername(String username);
}
