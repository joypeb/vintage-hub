package com.joypeb.vintagehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.joypeb.vintagehub.crawl.application.CrawlRunScheduleProperties;
import com.joypeb.vintagehub.product.application.ProductAvailabilityCheckProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ProductAvailabilityCheckProperties.class, CrawlRunScheduleProperties.class})
public class VintageHubBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(VintageHubBeApplication.class, args);
	}

}
