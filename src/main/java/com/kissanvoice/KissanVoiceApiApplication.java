package com.kissanvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling drives the outbox poller (see outbox.OutboxPublisher).
@SpringBootApplication
@EnableScheduling
public class KissanVoiceApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(KissanVoiceApiApplication.class, args);
	}

}
