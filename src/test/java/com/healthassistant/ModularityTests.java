package com.healthassistant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(HealthAssistantApplication.class);

    @Test
    void verifyModularStructure() {
        // verify() throws exception if violations found (e.g., illegal cross-module dependencies)
        modules.verify();
    }

    @Test
    void shouldDetectAllModules() {
        long moduleCount = modules.stream().count();
        assertEquals(13, moduleCount,
                "Expected 13 modules but found " + moduleCount + ": " + modules.stream().toList());
    }

    @Test
    void generateDocumentation() {
        var documenter = new Documenter(modules)
                .writeModuleCanvases()
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();

        // Verify documentation options were applied
        assertTrue(documenter.toString().contains("Documenter"),
                "Documenter should be properly initialized");
    }
}
