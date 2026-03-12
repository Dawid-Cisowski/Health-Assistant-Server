package com.healthassistant.mealimport;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
class MealImportAsyncConfig {

    @Bean("mealImportExecutor")
    @ConditionalOnMissingBean(name = "mealImportExecutor")
    Executor mealImportExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("meal-import-");
        executor.initialize();
        return executor;
    }
}
