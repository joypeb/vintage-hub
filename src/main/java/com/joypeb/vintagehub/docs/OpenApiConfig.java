package com.joypeb.vintagehub.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

	@Bean
	OpenAPI vintageHubOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("Vintage Hub API")
				.version("v1")
				.description("Vintage Hub backend API documentation."));
	}
}
