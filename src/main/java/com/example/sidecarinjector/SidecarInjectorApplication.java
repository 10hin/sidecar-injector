package com.example.sidecarinjector;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SidecarInjectorApplication {

	public static void main(String[] args) {
		final ApiClient client;
		try {
			client = Config.defaultClient();
			Configuration.setDefaultApiClient(client);
		} catch (final Throwable t) {
			throw new InternalError("failed to initialize application", t);
		}

		SpringApplication.run(SidecarInjectorApplication.class, args);
	}

}
