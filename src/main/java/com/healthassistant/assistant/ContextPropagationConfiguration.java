package com.healthassistant.assistant;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * Configuration for context propagation across reactive boundaries.
 *
 * <p>This configuration ensures that the AssistantContext (containing device ID)
 * is properly propagated when Spring AI invokes health tools on different threads
 * from Reactor's boundedElastic scheduler pool.
 *
 * <p>Without this, InheritableThreadLocal values would not propagate to reused threads,
 * causing tool calls to have null device ID.
 */
@Configuration
@ConditionalOnProperty(name = "app.assistant.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
class ContextPropagationConfiguration {

    @PostConstruct
    void enableContextPropagation() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new AssistantContextThreadLocalAccessor());
        Hooks.enableAutomaticContextPropagation();
        log.info("Enabled automatic context propagation for AssistantContext");
    }
}
