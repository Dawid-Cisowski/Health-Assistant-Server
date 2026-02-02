pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://repo.spring.io/milestone")
        }
    }
}

rootProject.name = "health-assistant-event-collector"

include("integration-tests")

