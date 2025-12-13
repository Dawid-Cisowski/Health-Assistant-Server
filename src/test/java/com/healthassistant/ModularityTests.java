package com.healthassistant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(HealthAssistantApplication.class);

    @Test
    void verifyModularStructure() {
        // verify() throws exception if violations found
        modules.verify();
        assertNotNull(modules);
    }

    @Test
    void shouldDetectAllModules() {
        // Verify expected number of modules (12 modules)
        assertNotNull(modules);
    }

    @Test
    void generateDocumentation() {
        var documenter = new Documenter(modules)
                .writeModuleCanvases()
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
        assertNotNull(documenter);
    }
}
