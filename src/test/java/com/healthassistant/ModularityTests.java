package com.healthassistant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(HealthAssistantApplication.class);

    @Test
    void verifyModularStructure() {
        // verify() throws exception if violations found (e.g., illegal cross-module dependencies, cycles)
        modules.verify();
    }

    @Test
    void shouldDetectAllExpectedModules() {
        var expectedModules = Set.of(
                "appevents", "healthevents", "dailysummary", "steps", "workout",
                "workoutimport", "sleep", "sleepimport", "calories", "activity",
                "meals", "mealimport", "googlefit", "assistant", "security", "config",
                "weight", "weightimport", "heartrate", "guardrails"
        );

        var actualModules = modules.stream()
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(actualModules).containsExactlyInAnyOrderElementsOf(expectedModules);
    }

    @Test
    void shouldHaveNoViolations() {
        // detectViolations() returns violations, throwIfPresent() throws if any exist
        var violations = modules.detectViolations();
        violations.throwIfPresent();
        // PMD requires explicit assertion
        assertThat(true).isTrue();
    }

    @Test
    void projectionModulesShouldNotDependOnImportModules() {
        var projectionModules = List.of("steps", "workout", "sleep", "calories", "activity", "meals", "weight");
        var importModules = Set.of("mealimport", "sleepimport", "workoutimport", "weightimport", "googlefit");

        for (String projName : projectionModules) {
            var module = modules.getModuleByName(projName).orElseThrow();
            var dependencies = module.getBootstrapDependencies(modules)
                    .map(ApplicationModule::getName)
                    .collect(Collectors.toSet());

            assertThat(dependencies)
                    .as("Module %s should not depend on import modules", projName)
                    .doesNotContainAnyElementsOf(importModules);
        }
    }

    @Test
    void healthEventsShouldNotDependOnProjectionModules() {
        var projectionModules = Set.of("steps", "workout", "sleep", "calories", "activity", "meals", "weight", "dailysummary");

        var module = modules.getModuleByName("healthevents").orElseThrow();
        var dependencies = module.getBootstrapDependencies(modules)
                .map(ApplicationModule::getName)
                .collect(Collectors.toSet());

        assertThat(dependencies)
                .as("healthevents should not depend on projection modules")
                .doesNotContainAnyElementsOf(projectionModules);
    }

    @Test
    void modulesShouldHaveProperlyNamedFacades() {
        var modulesWithFacades = Map.ofEntries(
                Map.entry("steps", "StepsFacade"),
                Map.entry("workout", "WorkoutFacade"),
                Map.entry("sleep", "SleepFacade"),
                Map.entry("calories", "CaloriesFacade"),
                Map.entry("activity", "ActivityFacade"),
                Map.entry("meals", "MealsFacade"),
                Map.entry("healthevents", "HealthEventsFacade"),
                Map.entry("dailysummary", "DailySummaryFacade"),
                Map.entry("assistant", "AssistantFacade"),
                Map.entry("googlefit", "GoogleFitFacade"),
                Map.entry("mealimport", "MealImportFacade"),
                Map.entry("sleepimport", "SleepImportFacade"),
                Map.entry("workoutimport", "WorkoutImportFacade"),
                Map.entry("weight", "WeightFacade"),
                Map.entry("weightimport", "WeightImportFacade"),
                Map.entry("heartrate", "HeartRateFacade")
        );

        for (var entry : modulesWithFacades.entrySet()) {
            String moduleName = entry.getKey();
            String facadeName = entry.getValue();
            String fullClassName = "com.healthassistant.%s.api.%s".formatted(moduleName, facadeName);

            assertThatCode(() -> Class.forName(fullClassName))
                    .as("Module %s should have facade %s", moduleName, facadeName)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void generateDocumentation() {
        var documenter = new Documenter(modules)
                .writeModuleCanvases()
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
        // PMD requires explicit assertion
        assertThat(documenter).isNotNull();
    }
}
