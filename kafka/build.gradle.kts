/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    id("tracker.kotlin-library-conventions")
    id("maven-publish")
    kotlin("plugin.serialization") version "1.9.10"
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/nabeelvalley/kotlin-kafka")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("TOKEN")
            }
            group = "za.co.nabeelvalley"
            version = "0.0.0"
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components.findByName("java"))
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    api("org.apache.kafka:kafka-clients:3.6.0")
    api("org.apache.kafka:kafka-streams:3.5.1")
}