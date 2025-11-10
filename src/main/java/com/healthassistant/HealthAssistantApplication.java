package com.healthassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class HealthAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthAssistantApplication.class, args);
    }
}

