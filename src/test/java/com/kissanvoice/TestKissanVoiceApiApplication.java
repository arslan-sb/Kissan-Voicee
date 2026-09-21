package com.kissanvoice;

import org.springframework.boot.SpringApplication;

public class TestKissanVoiceApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(KissanVoiceApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
