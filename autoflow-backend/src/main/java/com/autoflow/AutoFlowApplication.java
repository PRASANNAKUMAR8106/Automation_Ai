package com.autoflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AutoFlow AI Core Backend Application.
 * Modular Monolith architecture running on Spring Boot 3.3+ and Java 21 LTS with Virtual Threads.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class AutoFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoFlowApplication.class, args);
    }
}
