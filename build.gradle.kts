plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version("6.0.0")
}

repositories {
    // Use jcenter for resolving dependencies.
    jcenter()
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}

dependencies {
    // Align versions of all Kotlin components
    compileOnly(platform("org.jetbrains.kotlin:kotlin-bom"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.8")

    // Use the Kotlin JDK 8 standard library.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Normal dependencies
    implementation("io.vertx:vertx-auth-jwt:4.0.0")
    implementation("de.mkammerer:argon2-jvm:2.8")
    implementation("net.bramp.ffmpeg:ffmpeg:0.6.2")

    // Database migration
    implementation("org.flywaydb:flyway-core:7.3.2")
    implementation("postgresql:postgresql:9.1-901-1.jdbc4")

    // Twine, does not get packaged
    compileOnly("net.termer.twine:twine:2.0")
}