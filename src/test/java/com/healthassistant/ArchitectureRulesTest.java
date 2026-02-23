package com.healthassistant;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.healthassistant", importOptions = ImportOption.DoNotIncludeTests.class)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // ArchUnit uses @ArchTest instead of @Test
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule facadesShouldBeInterfaces =
            classes().that().haveSimpleNameEndingWith("Facade")
                    .should().beInterfaces()
                    .because("Facades define module contracts and should be interfaces");

    @ArchTest
    static final ArchRule facadesShouldResideInApiPackage =
            classes().that().haveSimpleNameEndingWith("Facade")
                    .should().resideInAPackage("..api..")
                    .because("Facades are public API and should be in api subpackage");

    @ArchTest
    static final ArchRule domainServicesShouldNotBePublic =
            classes().that().haveSimpleNameEndingWith("Service")
                    .and().areNotInterfaces()
                    .and().resideInAnyPackage(
                            "com.healthassistant.steps..",
                            "com.healthassistant.workout..",
                            "com.healthassistant.sleep..",
                            "com.healthassistant.calories..",
                            "com.healthassistant.activity..",
                            "com.healthassistant.meals..",
                            "com.healthassistant.healthevents..",
                            "com.healthassistant.dailysummary..",
                            "com.healthassistant.weight..",
                            "com.healthassistant.heartrate..",
                            "com.healthassistant.reports..",
                            "com.healthassistant.mealcatalog.."
                    )
                    .and().resideOutsideOfPackage("..api..")
                    .should().notBePublic()
                    .because("Domain service implementations should be package-private");

    @ArchTest
    static final ArchRule projectorsShouldBePackagePrivate =
            classes().that().haveSimpleNameEndingWith("Projector")
                    .should().notBePublic()
                    .because("Projectors are internal implementation details");

    @ArchTest
    static final ArchRule noDirectJpaEntityAccess =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaEntity")
                    .because("API layer should not directly access JPA entities");

    @ArchTest
    static final ArchRule controllersShouldBeInExpectedModules =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAnyPackage(
                            "com.healthassistant.appevents..",
                            "com.healthassistant.assistant..",
                            "com.healthassistant.googlefit..",
                            "com.healthassistant.steps..",
                            "com.healthassistant.workout..",
                            "com.healthassistant.sleep..",
                            "com.healthassistant.meals..",
                            "com.healthassistant.mealimport..",
                            "com.healthassistant.sleepimport..",
                            "com.healthassistant.workoutimport..",
                            "com.healthassistant.dailysummary..",
                            "com.healthassistant.calories..",
                            "com.healthassistant.activity..",
                            "com.healthassistant.config..",
                            "com.healthassistant.weight..",
                            "com.healthassistant.weightimport..",
                            "com.healthassistant.heartrate..",
                            "com.healthassistant.bodymeasurements..",
                            "com.healthassistant.reports.."
                    )
                    .because("Controllers should only exist in API-facing modules");

    @ArchTest
    static final ArchRule commandHandlersShouldBePackagePrivate =
            classes().that().haveSimpleNameEndingWith("CommandHandler")
                    .should().notBePublic()
                    .because("Command handlers are internal implementation details");

    @ArchTest
    static final ArchRule aggregatorsShouldBePackagePrivate =
            classes().that().haveSimpleNameEndingWith("Aggregator")
                    .should().notBePublic()
                    .because("Aggregators are internal implementation details");

    @ArchTest
    static final ArchRule listenersShouldBePackagePrivate =
            classes().that().haveSimpleNameEndingWith("Listener")
                    .and().haveSimpleNameNotContaining("Event")
                    .should().notBePublic()
                    .because("Listeners are internal implementation details");
}
