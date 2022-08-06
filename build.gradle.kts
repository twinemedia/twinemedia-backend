import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
	// Apply the Kotlin JVM plugin to add support for Kotlin
	kotlin("jvm") version "1.7.10"

	id("com.github.johnrengelman.shadow") version("7.0.0")
	id("org.asciidoctor.jvm.convert") version("3.1.0")
}

repositories {
	mavenCentral()
}

kotlin {
	jvmToolchain {
		languageVersion.set(JavaLanguageVersion.of("8")) // "8"
	}
}

dependencies {
	// Align versions of all Kotlin components
	compileOnly(platform("org.jetbrains.kotlin:kotlin-bom"))
	compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

	// Use the Kotlin JDK 8 standard library.
	compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

	// Normal dependencies
	implementation("io.vertx:vertx-auth-jwt:4.1.2")
	implementation("de.mkammerer:argon2-jvm:2.10.1")
	implementation("net.bramp.ffmpeg:ffmpeg:0.6.2")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
	implementation("net.termer.vertx.kotlin.validation:vertx-web-validator-kotlin:1.0.1")

	// Amazon S3
	implementation(platform("software.amazon.awssdk:bom:2.15.45"))
	implementation("software.amazon.awssdk:s3")
	implementation("io.reactiverse:aws-sdk:1.0.0")

	// Database
	implementation("org.flywaydb:flyway-core:7.3.2")
	implementation("io.vertx:vertx-sql-client-templates:4.1.2")
	implementation("com.ongres.scram:client:2.1")

	// Twine, does not get packaged
	compileOnly("net.termer.twine:twine:2.1b")
}

tasks {
	"asciidoctor"(AsciidoctorTask::class) {
		setSourceDir(file("docs"))
		sources(delegateClosureOf<PatternSet> {
			include("index.adoc"/*, "another.adoc", "third.adoc"*/)
		})
		setOutputDir(file("build/docs"))
	}
}

// Task dependencies
tasks.named("build") {
	dependsOn(":shadowJar")
}