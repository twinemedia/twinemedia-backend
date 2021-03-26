import org.asciidoctor.gradle.jvm.AsciidoctorTask

plugins {
	// Apply the Kotlin JVM plugin to add support for Kotlin.
	id("org.jetbrains.kotlin.jvm") version "1.3.72"
	id("com.github.johnrengelman.shadow") version("6.0.0")
	id("org.asciidoctor.jvm.convert") version("3.1.0")
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
	implementation("de.mkammerer:argon2-jvm:2.9")
	implementation("net.bramp.ffmpeg:ffmpeg:0.6.2")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.10.0")
	implementation("net.termer.vertx.kotlin.validation:vertx-web-validator-kotlin:1.0.1")

	// Database
	implementation("org.flywaydb:flyway-core:7.3.2")
	implementation("postgresql:postgresql:9.1-901-1.jdbc4")
	implementation("io.vertx:vertx-sql-client-templates:4.0.0")
	implementation("io.vertx:vertx-codegen:4.0.0")
	annotationProcessor("io.vertx:vertx-codegen:4.0.0:processor")

	// Twine, does not get packaged
	compileOnly("net.termer.twine:twine:2.0")
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