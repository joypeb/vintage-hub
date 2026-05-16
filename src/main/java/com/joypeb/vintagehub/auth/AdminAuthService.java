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
		// 사용자명, 활성화 여부, 비밀번호 해시 검증을 모두 통과한 관리자만 로그인시킨다.
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
		// 인증이 끝나면 관리자 API에서 사용할 Bearer 토큰을 발급한다.
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
			// 운영 환경에서 해시 생성 API가 노출되지 않도록 설정값으로 차단한다.
			log.atWarn()
				.addKeyValue("event", "auth.password_hash.rejected")
				.addKeyValue("reason", "disabled")
				.log("auth.password_hash.rejected");
			throw new PasswordHashApiDisabledException();
		}
		if (!hasText(password)) {
			// 빈 비밀번호는 해시로 만들지 않고 잘못된 인증 입력과 동일하게 처리한다.
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
