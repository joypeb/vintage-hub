package com.joypeb.vintagehub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class AdminAuthService {

	private final AdminAuthProperties adminAuthProperties;
	private final AdminUserRepository adminUserRepository;
	private final JwtTokenProvider jwtTokenProvider;
	private final PasswordEncoder passwordEncoder;

	AdminAuthService(AdminAuthProperties adminAuthProperties, AdminUserRepository adminUserRepository,
			JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
		this.adminAuthProperties = adminAuthProperties;
		this.adminUserRepository = adminUserRepository;
		this.jwtTokenProvider = jwtTokenProvider;
		this.passwordEncoder = passwordEncoder;
	}

	LoginResult login(String username, String password) {
		AdminUserEntity adminUser = adminUserRepository.findByUsername(username)
			.filter(AdminUserEntity::enabled)
			.filter(user -> passwordEncoder.matches(password, user.passwordHash()))
			.orElseThrow(InvalidAdminCredentialsException::new);
		JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueAdminToken(adminUser.username());
		return new LoginResult(issuedToken.value(), "Bearer", jwtTokenProvider.accessTokenValiditySeconds());
	}

	String encodePassword(String password) {
		if (!adminAuthProperties.passwordHashApiEnabled()) {
			throw new PasswordHashApiDisabledException();
		}
		if (!hasText(password)) {
			throw new InvalidAdminCredentialsException();
		}
		return passwordEncoder.encode(password);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	record LoginResult(String accessToken, String tokenType, long expiresIn) {
	}
}
