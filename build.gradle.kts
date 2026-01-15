group = "me.dafnik"
version = project.findProperty("version")?.toString() ?: "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}

plugins {
    `maven-publish`

    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"

    id("io.gitlab.arturbosch.detekt") version "1.23.8"

    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("org.springframework.data:spring-data-jpa")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("dev.zacsweers.kctfork:core:0.12.1")

    testRuntimeOnly("com.h2database:h2")
    testImplementation(kotlin("test"))
}

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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

tasks.getByName<Jar>("jar") {
    enabled = true
    archiveClassifier.set("") // removes "-plain" to work with jitpack
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
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