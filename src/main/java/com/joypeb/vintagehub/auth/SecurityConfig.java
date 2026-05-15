package com.joypeb.vintagehub.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joypeb.vintagehub.common.api.ApiResponse;
import com.joypeb.vintagehub.common.api.ErrorCode;
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
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/password-hash").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.anyRequest().permitAll())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint((request, response, authException) -> {
					log.atWarn()
						.addKeyValue("event", "auth.request.rejected")
						.addKeyValue("path", request.getRequestURI())
						.addKeyValue("status", 401)
						.addKeyValue("reason", "unauthenticated")
						.log("auth.request.rejected");
					writeAuthError(response, ErrorCode.UNAUTHORIZED, "인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
				})
				.accessDeniedHandler((request, response, accessDeniedException) -> {
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
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	private void writeAuthError(jakarta.servlet.http.HttpServletResponse response, ErrorCode errorCode, String message,
			HttpStatus status) throws java.io.IOException {
		response.setStatus(status.value());
		response.setHeader("WWW-Authenticate", "Bearer");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode, message)));
	}
}
