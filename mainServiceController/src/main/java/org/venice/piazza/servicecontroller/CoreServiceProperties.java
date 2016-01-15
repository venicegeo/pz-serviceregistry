package org.venice.piazza.servicecontroller;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(locations = "classpath:application.properties", ignoreUnknownFields = false, prefix = "core")
public class CoreServiceProperties {
	
	@NotBlank
	private String uuidservice;

	public String getUuidservice() {
		return uuidservice;
	}

	public void setUuidservice(String uuidservice) {
		this.uuidservice = uuidservice;
	}

}
