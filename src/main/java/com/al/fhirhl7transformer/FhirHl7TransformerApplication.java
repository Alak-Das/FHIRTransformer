package com.al.fhirhl7transformer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FhirHl7TransformerApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirHl7TransformerApplication.class, args);
	}

}
