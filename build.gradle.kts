plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"

    id("io.gitlab.arturbosch.detekt") version "1.23.8"

    id("org.springframework.boot") version "3.3.2" // or latest
    id("io.spring.dependency-management") version "1.1.7"
}

group = "me.dafnik"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // H2 in-memory database
    runtimeOnly("com.h2database:h2")

    // Database driver (choose one)
    runtimeOnly("org.postgresql:postgresql") // PostgreSQL
    // runtimeOnly("com.mysql:mysql-connector-j") // MySQL/MariaDB
    // runtimeOnly("com.h2database:h2") // In-memory DB for testing

    // Kotlin reflection (needed for KProperty1)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Jakarta Persistence API (Criteria API)
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")

    // Optional: For testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    detekt {
//        config.from(rootDir.resolve("detekt.yml"))
        buildUponDefaultConfig = true
        basePath = rootDir.path
        // Autocorrection can only be done locally
        autoCorrect = System.getenv("CI")?.lowercase() != true.toString()
    }

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    }
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

kotlin {
    jvmToolchain(21)
}