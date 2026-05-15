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
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", username);
		payload.put("role", "ADMIN");
		payload.put("iat", issuedAt.getEpochSecond());
		payload.put("exp", expiresAt.getEpochSecond());
		String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
		return new IssuedToken(unsignedToken + "." + sign(unsignedToken), expiresAt);
	}

	Optional<String> validateAdminToken(String token) {
		try {
			String[] parts = token.split("\\.");
			if (parts.length != 3) {
				return Optional.empty();
			}
			String unsignedToken = parts[0] + "." + parts[1];
			if (!MessageDigest.isEqual(sign(unsignedToken).getBytes(StandardCharsets.UTF_8),
					parts[2].getBytes(StandardCharsets.UTF_8))) {
				return Optional.empty();
			}
			Map<String, Object> payload = objectMapper.readValue(BASE64_URL_DECODER.decode(parts[1]), MAP_TYPE);
			if (!"ADMIN".equals(payload.get("role"))) {
				return Optional.empty();
			}
			Number expiresAt = (Number) payload.get("exp");
			if (expiresAt == null || expiresAt.longValue() <= clock.instant().getEpochSecond()) {
				return Optional.empty();
			}
			Object subject = payload.get("sub");
			if (!(subject instanceof String username) || username.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(username);
		}
		catch (RuntimeException | java.io.IOException exception) {
			return Optional.empty();
		}
	}

	long accessTokenValiditySeconds() {
		return jwtProperties.accessTokenValiditySeconds();
	}

	private String encodeJson(Map<String, Object> value) {
		try {
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
