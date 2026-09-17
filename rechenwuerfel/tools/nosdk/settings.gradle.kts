// Hilfsprojekt der SDK-freien Pipeline: kompiliert nur den Kotlin/Java-Code.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "rechenwuerfel-nosdk"
