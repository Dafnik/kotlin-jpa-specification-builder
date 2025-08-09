group = "me.dafnik"
version = project.findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

plugins {
    `maven-publish`

    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"

    id("io.gitlab.arturbosch.detekt") version "1.23.8"

    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.7"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

detekt {
    buildUponDefaultConfig = true
    basePath = rootDir.path
    autoCorrect = System.getenv("CI")?.lowercase() != true.toString()
}

/**
 * Needs to be set if the project kotlin version is not supported by detekt.
 */
configurations.detekt {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21") // Add the version of Kotlin that detekt needs
        }
    }
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = true
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "me.dafnik"
            artifactId = "kotlin-jpa-specification-builder"
            version = project.findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

            from(components["java"])
        }
    }
}