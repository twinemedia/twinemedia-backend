import java.net.URI
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.8.0"

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

val jvmVersion = 17
val vertxVersion = "4.3.8"

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
    implementation("io.vertx:vertx-web-openapi:$vertxVersion")
    implementation("io.vertx:vertx-redis-client:$vertxVersion")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.5")

    // Misc dependencies
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("net.bramp.ffmpeg:ffmpeg:0.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")
    implementation("net.termer.vertx.kotlin.validation:vertx-web-validator-kotlin:2.0.0")
    implementation("net.termer.krestx:krestx-api:1.1.2")

    // Amazon S3
    implementation(platform("software.amazon.awssdk:bom:2.15.45"))
    implementation("software.amazon.awssdk:s3")
    implementation("io.reactiverse:aws-sdk:1.0.0")

    // Database
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    implementation("org.flywaydb:flyway-core:9.10.2")
    implementation("com.ongres.scram:client:2.1")
    implementation("commons-cli:commons-cli:1.5.0")
    implementation("org.jooq:jooq:3.17.6")

    // Code generation
    implementation("com.github.wowselim.eventbus-service:eventbus-service-core:2.1.0")
    kapt("com.github.wowselim.eventbus-service:eventbus-service-codegen:2.1.0")
}

application {
    mainClass.set("net.termer.twinemedia.App")
}

// Use Java 17 bytecode
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = jvmVersion.toString()
        languageVersion = "1.8"
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmVersion))
        implementation.set(JvmImplementation.J9)
    }
}


// Task dependencies
tasks.named("build") {
    dependsOn("shadowJar")
}
