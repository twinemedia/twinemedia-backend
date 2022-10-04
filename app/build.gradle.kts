import java.net.URI

plugins {
    val kotlinVersion = "1.7.10"
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion

    id("com.github.johnrengelman.shadow") version("7.0.0")

    application
}

repositories {
    mavenCentral()
    maven {
        url = URI("https://jitpack.io")
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("17"))
    }
}

val vertxVersion = "4.3.2"

dependencies {
    // Kotlin dependencies
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    // Vert.x
    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-auth-jwt:$vertxVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")

    // Misc dependencies
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("net.bramp.ffmpeg:ffmpeg:0.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("net.termer.vertx.kotlin.validation:vertx-web-validator-kotlin:2.0.0")
    implementation("net.termer.krestx:krestx-api:1.0.0")

    // Amazon S3
    implementation(platform("software.amazon.awssdk:bom:2.15.45"))
    implementation("software.amazon.awssdk:s3")
    implementation("io.reactiverse:aws-sdk:1.0.0")

    // Database
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    implementation("org.flywaydb:flyway-core:9.0.4")
    implementation("com.ongres.scram:client:2.1")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("org.jooq:jooq:3.17.4")

    // Code generation
    implementation("com.github.wowselim.eventbus-service:eventbus-service-core:2.1.0")
    kapt("com.github.wowselim.eventbus-service:eventbus-service-codegen:2.1.0")
}

application {
    mainClass.set("net.termer.twinemedia.App")
}

// Task dependencies
tasks.named("build") {
    dependsOn("shadowJar")
}