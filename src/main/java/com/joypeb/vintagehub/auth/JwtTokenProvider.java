package com.joypeb.vintagehub.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
class JwtTokenProvider {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final JwtProperties jwtProperties;
	private final Clock clock;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	JwtTokenProvider(JwtProperties jwtProperties) {
		this(jwtProperties, Clock.systemUTC());
	}

	JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
		this.jwtProperties = jwtProperties;
		this.clock = clock;
	}

	IssuedToken issueAdminToken(String username) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenValiditySeconds());
		// HS256 JWT 표준 헤더를 직접 구성해 외부 JWT 라이브러리 없이 토큰을 만든다.
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");
		Map<String, Object> payload = new LinkedHashMap<>();
		// 관리자 인증만 지원하므로 role 클레임을 고정하고 sub에 사용자명을 넣는다.
		payload.put("sub", username);
		payload.put("role", "ADMIN");
		payload.put("iat", issuedAt.getEpochSecond());
		payload.put("exp", expiresAt.getEpochSecond());
		String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
		// header.payload 문자열에 HMAC 서명을 붙여 최종 JWT 형식을 만든다.
		return new IssuedToken(unsignedToken + "." + sign(unsignedToken), expiresAt);
	}

	Optional<String> validateAdminToken(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				// JWT는 header.payload.signature 세 조각이어야 한다.
				return Optional.empty();
			}
			String unsignedToken = parts[0] + "." + parts[1];
			if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8),
					parts[2].getBytes(StandardCharsets.UTF_8))) {
				// 상수 시간 비교로 서명 불일치 여부를 확인한다.
				return Optional.empty();
			}
			Map<String, Object> payload = objectMapper.readValue(BASE64_URL_DECODER.decode(parts[1]), MAP_TYPE);
			if (!"ADMIN".equals(payload.get("role"))) {
				// 관리자 API 접근 토큰만 허용한다.
				return Optional.empty();
			}
			Number expiresAt = (Number) payload.get("exp");
			if (expiresAt == null || expiresAt.longValue() <= clock.instant().getEpochSecond()) {
				// 만료 시간이 없거나 현재 시각을 지난 토큰은 거부한다.
				return Optional.empty();
			}
			Object subject = payload.get("sub");
			if (!(subject instanceof String username) || username.isBlank()) {
				// 인증 컨텍스트에 넣을 사용자명이 없으면 유효한 관리자 토큰으로 볼 수 없다.
				return Optional.empty();
			}
			return Optional.of(username);
		}
		catch (RuntimeException | java.io.IOException exception) {
			// 파싱 실패와 타입 오류는 인증 실패로만 취급해 세부 원인을 외부에 노출하지 않는다.
			return Optional.empty();
		}
	}

	long accessTokenValiditySeconds() {
		return jwtProperties.accessTokenValiditySeconds();
	}

	private String encodeJson(Map<String, Object> value) {
		try {
			// JWT 규격에 맞게 padding 없는 Base64 URL 인코딩을 사용한다.
			return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
		}
		catch (java.io.IOException exception) {
			throw new IllegalStateException("JWT JSON serialization failed.", exception);
		}
	}

	private String sign(String unsignedToken) {
		if (jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
			throw new IllegalStateException("JWT secret is not configured.");
		}
		try {
			// 설정된 공유 비밀키로 header.payload 값을 HMAC-SHA256 서명한다.
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return BASE64_URL_ENCODER.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.GeneralSecurityException exception) {
			throw new IllegalStateException("JWT signing failed.", exception);
		}
	}

	record IssuedToken(String value, Instant expiresAt) {
	}
}
