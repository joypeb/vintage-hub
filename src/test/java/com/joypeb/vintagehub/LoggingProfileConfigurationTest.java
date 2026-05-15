package com.joypeb.vintagehub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingProfileConfigurationTest {

	@Test
	void localAndDevProfilesEnableApplicationDebugLogs() {
		assertApplicationLogLevel("application-local.yml", "DEBUG");
		assertApplicationLogLevel("application-dev.yml", "DEBUG");
		assertStructuredConsoleFormat("application-local.yml");
		assertStructuredConsoleFormat("application-dev.yml");
	}

	@Test
	void prodProfileEnablesOnlyApplicationInfoLogs() {
		assertApplicationLogLevel("application-prod.yml", "INFO");
		assertStructuredConsoleFormat("application-prod.yml");
	}

	private void assertApplicationLogLevel(String resourceName, String expectedLevel) {
		Properties properties = yamlProperties(resourceName);

		assertThat(properties.getProperty("logging.level.com.joypeb.vintagehub")).isEqualTo(expectedLevel);
	}

	private void assertStructuredConsoleFormat(String resourceName) {
		Properties properties = yamlProperties(resourceName);

		assertThat(properties.getProperty("logging.structured.format.console")).isEqualTo("logstash");
	}

	private Properties yamlProperties(String resourceName) {
		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(new ClassPathResource(resourceName));
		return factory.getObject();
	}
}
