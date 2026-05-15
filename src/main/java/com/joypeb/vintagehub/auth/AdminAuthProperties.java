package com.joypeb.vintagehub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vintage-hub.admin")
record AdminAuthProperties(boolean passwordHashApiEnabled) {
}
