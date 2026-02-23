package com.its.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer.mcpServerOAuth2;

@SpringBootApplication
public class SchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchedulerApplication.class, args);
	}

	@Bean
	Customizer<HttpSecurity> httpSecurityCustomizer(
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer
	) {
		return http -> http
				.with(mcpServerOAuth2(), a -> a.authorizationServer(issuer));
	}
}

@Service
@Slf4j
class DogAdoptionScheduler {
	@McpTool(description = "Schedule an appointment to pick up or adopt a dog from Pooch Palace location")
	/* Commented below logic to send DogAdoptionResponse
	String schedule(@McpToolParam int dogId) {
		var i = Instant
				.now()
				.plus(3, ChronoUnit.DAYS)
				.toString();
		log.info("Scheduling dogId : {} for i : {} ", dogId, i);
		return i;
	}*/

	DogAdoptionResponse schedule(@McpToolParam int dogId) {
		var user = SecurityContextHolder
				.getContext()
				.getAuthentication()
				.getName();

		var i = Instant
				.now()
				.plus(3, ChronoUnit.DAYS);
				//.toString();
		log.info("Scheduling dogId : {} for i : {} and requester : {} ", dogId, i, user);
		return new DogAdoptionResponse(i, user);
	}
}

record DogAdoptionResponse(Instant scheduled, String requester) {}
