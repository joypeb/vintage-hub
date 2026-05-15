package com.joypeb.vintagehub.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class AdminAuthService {

	private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

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
			.orElseThrow(() -> {
				log.atWarn()
					.addKeyValue("event", "auth.login.failed")
					.addKeyValue("username", username)
					.addKeyValue("reason", "invalid-credentials")
					.log("auth.login.failed");
				return new InvalidAdminCredentialsException();
			});
		JwtTokenProvider.IssuedToken issuedToken = jwtTokenProvider.issueAdminToken(adminUser.username());
		log.atInfo()
			.addKeyValue("event", "auth.login.succeeded")
			.addKeyValue("username", adminUser.username())
			.addKeyValue("expiresAt", issuedToken.expiresAt())
			.log("auth.login.succeeded");
		return new LoginResult(issuedToken.value(), "Bearer", jwtTokenProvider.accessTokenValiditySeconds());
	}

	String encodePassword(String password) {
		if (!adminAuthProperties.passwordHashApiEnabled()) {
			log.atWarn()
				.addKeyValue("event", "auth.password_hash.rejected")
				.addKeyValue("reason", "disabled")
				.log("auth.password_hash.rejected");
			throw new PasswordHashApiDisabledException();
		}
		if (!hasText(password)) {
			log.atWarn()
				.addKeyValue("event", "auth.password_hash.rejected")
				.addKeyValue("reason", "blank-password")
				.log("auth.password_hash.rejected");
			throw new InvalidAdminCredentialsException();
		}
		log.atInfo()
			.addKeyValue("event", "auth.password_hash.created")
			.log("auth.password_hash.created");
		return passwordEncoder.encode(password);
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	record LoginResult(String accessToken, String tokenType, long expiresIn) {
	}
}
