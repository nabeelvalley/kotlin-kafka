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
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("PASSWORD")
            }
        }
    }
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            groupId = "za.co.nabeelvalley"
            version = "0.0.1"

            pom {
                name = "Kafka"
                description = "Kafka for Kotlin"
                url = "https://github.com/nabeelvalley/kotlin-kafka"
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    api("org.apache.kafka:kafka-clients:3.6.0")
    api("org.apache.kafka:kafka-streams:3.5.1")
}