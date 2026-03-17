plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    kotlin("kapt") version "1.9.25"
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "pgcore"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val gopLoggingVersion = (findProperty("gopLoggingVersion") as String?)
    ?: System.getenv("LOGGING_LIB_VERSION")
    ?: System.getenv("GOP_LOGGING_VERSION")
    ?: readDotEnvValue("GOP_LOGGING_VERSION")
    ?: throw IllegalStateException("GOP_LOGGING_VERSION is required. Set it in pg_core/.env or environment variable.")

val githubPackagesUrl = (findProperty("githubPackagesUrl") as String?)
    ?: System.getenv("PACKAGES_URL")
    ?: System.getenv("GITHUB_PACKAGES_URL")
    ?: readDotEnvValue("GITHUB_PACKAGES_URL")
    ?: throw IllegalStateException("GITHUB_PACKAGES_URL is required. Set it in pg_core/.env or environment variable.")

val githubPackagesUser = (findProperty("githubPackagesUser") as String?)
    ?: System.getenv("PACKAGES_USER")
    ?: System.getenv("GITHUB_PACKAGES_USER")
    ?: readDotEnvValue("GITHUB_PACKAGES_USER")
    ?: throw IllegalStateException("GITHUB_PACKAGES_USER is required. Set it in pg_core/.env or environment variable.")

val githubPackagesToken = (findProperty("githubPackagesToken") as String?)
    ?: System.getenv("PACKAGES_TOKEN")
    ?: System.getenv("GITHUB_PACKAGES_TOKEN")
    ?: readDotEnvValue("GITHUB_PACKAGES_TOKEN")
    ?: throw IllegalStateException("GITHUB_PACKAGES_TOKEN is required. Set it in pg_core/.env or environment variable.")

fun readDotEnvValue(key: String): String? {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return null

    return envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim().removeSurrounding("\"")
            if (k == key && v.isNotEmpty()) v else null
        }
        .firstOrNull()
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        url = uri(githubPackagesUrl)
        credentials {
            username = githubPackagesUser
            password = githubPackagesToken
        }
    }
}

dependencies {
    // --- Spring Boot Starters ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Kotlin ---
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // --- DB Driver (MySQL) ---
    runtimeOnly("com.mysql:mysql-connector-j")

    // --- QueryDSL ---
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")

    // --- API Docs (Swagger / OpenAPI) ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.16")

    // --- Monitoring ---
    implementation("io.micrometer:micrometer-registry-prometheus")

    // --- AWS SQS ---
    implementation("software.amazon.awssdk:sqs:2.30.0")

    // --- Testing ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(module = "mockito-core")
    }

    // --- Redis ---
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // --- HTTP Connection Pool ---
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // --- gop logging lib ---
    implementation("com.gop.logging:gop-logging-contract:$gopLoggingVersion")
    implementation("com.gop.logging:gop-logging-spring:$gopLoggingVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("com.pgcore.PgcoreApplicationKt")
}
