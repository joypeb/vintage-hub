package com.joypeb.vintagehub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vintage-hub.jwt")
record JwtProperties(String secret, long accessTokenValiditySeconds) {

	JwtProperties {
		if (accessTokenValiditySeconds == 0) {
			accessTokenValiditySeconds = 1800;
		}
	}
}
