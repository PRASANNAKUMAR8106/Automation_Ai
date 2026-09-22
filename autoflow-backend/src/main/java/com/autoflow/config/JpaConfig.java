package com.autoflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration separated from main application class to allow clean WebMvcTest slices.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
