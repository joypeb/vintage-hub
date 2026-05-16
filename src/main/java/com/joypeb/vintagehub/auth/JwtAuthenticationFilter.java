package com.joypeb.vintagehub.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;

	JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
			// Bearer 토큰이 있는 요청만 JWT 검증을 수행하고, 공개 API는 그대로 다음 필터로 넘긴다.
			String token = authorization.substring(BEARER_PREFIX.length());
			jwtTokenProvider.validateAdminToken(token).ifPresentOrElse(username -> {
				// 유효한 관리자 토큰이면 Spring Security 컨텍스트에 ROLE_ADMIN 인증을 심는다.
				SecurityContextHolder.getContext()
					.setAuthentication(new UsernamePasswordAuthenticationToken(username, token,
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
				log.atDebug()
					.addKeyValue("event", "auth.jwt.accepted")
					.addKeyValue("username", username)
					.addKeyValue("path", request.getRequestURI())
					.log("auth.jwt.accepted");
			}, () -> log.atWarn()
				.addKeyValue("event", "auth.jwt.rejected")
				.addKeyValue("path", request.getRequestURI())
				.addKeyValue("reason", "invalid-token")
				.log("auth.jwt.rejected"));
		}
		filterChain.doFilter(request, response);
	}
}
