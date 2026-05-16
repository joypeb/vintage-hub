package com.joypeb.vintagehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.common.api.ErrorCode;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties({ AdminAuthProperties.class, JwtProperties.class })
class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
		// REST API는 JWT 기반 무상태 인증만 사용하므로 CSRF와 세션 생성을 비활성화한다.
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				// 비동기/SSE 완료와 에러 디스패치는 인증 필터에서 막지 않는다.
				.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/password-hash").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().permitAll())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint((request, response, authException) -> {
					// 인증 실패도 공통 ApiResponse 형식으로 내려 관리자 화면에서 일관되게 처리한다.
					log.atWarn()
						.addKeyValue("event", "auth.request.rejected")
						.addKeyValue("path", request.getRequestURI())
						.addKeyValue("status", 401)
						.addKeyValue("reason", "unauthenticated")
						.log("auth.request.rejected");
					writeAuthError(response, ErrorCode.UNAUTHORIZED, "인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
				})
				.accessDeniedHandler((request, response, accessDeniedException) -> {
					// 인증은 되었지만 ROLE_ADMIN 권한이 없으면 403으로 응답한다.
					log.atWarn()
						.addKeyValue("event", "auth.request.rejected")
						.addKeyValue("path", request.getRequestURI())
						.addKeyValue("status", 403)
						.addKeyValue("reason", "access-denied")
						.log("auth.request.rejected");
					writeAuthError(response, ErrorCode.FORBIDDEN, "권한이 부족합니다.", HttpStatus.FORBIDDEN);
				}))
			.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		// Spring Security 기본 delegating encoder로 저장된 해시의 알고리즘 prefix를 지원한다.
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private void writeAuthError(jakarta.servlet.http.HttpServletResponse response, ErrorCode errorCode, String message,
			HttpStatus status) throws java.io.IOException {
		// 필터 단계 예외는 @ControllerAdvice를 거치지 않으므로 여기서 직접 JSON 응답을 작성한다.
		response.setStatus(status.value());
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, message)));
	}
}
