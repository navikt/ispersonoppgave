import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

object Versions {
    const val avroVersion = "1.10.0"
    const val confluentVersion = "5.5.0"
    const val coroutinesVersion = "1.4.2"
    const val flywayVersion = "7.11.0"
    const val hikariVersion = "4.0.3"
    const val kafkaVersion = "2.7.0"
    const val kafkaEmbeddedVersion = "2.5.0"
    const val kluentVersion = "1.61"
    const val kotlinSerializationVersion = "0.20.0"
    const val ktorVersion = "1.3.2"
    const val logbackVersion = "1.2.3"
    const val logstashEncoderVersion = "6.3"
    const val postgresVersion = "42.2.18"
    const val postgresEmbeddedVersion = "0.13.3"
    const val prometheusVersion = "0.9.0"
    const val vaultJavaDriveVersion = "3.1.0"
    const val spekVersion = "2.0.15"
    const val jacksonVersion = "2.11.3"
    const val mockkVersion = "1.10.5"
    const val nimbusjosejwtVersion = "7.5.1"
    const val syfoOppfolgingsplanSchemaVersion = "1.0.2"
}

plugins {
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://repository.mulesoft.org/nexus/content/repositories/public/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfoopservice-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${Versions.coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutinesVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:${Versions.kotlinSerializationVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-common:${Versions.kotlinSerializationVersion}")

    implementation("io.prometheus:simpleclient_hotspot:${Versions.prometheusVersion}")
    implementation("io.prometheus:simpleclient_common:${Versions.prometheusVersion}")

    implementation("io.ktor:ktor-server-netty:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-auth-basic-jvm:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-logging:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.ktorVersion}")

    implementation("io.ktor:ktor-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-auth:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-auth-jwt:${Versions.ktorVersion}")

    implementation("ch.qos.logback:logback-classic:${Versions.logbackVersion}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoderVersion}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jacksonVersion}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVersion}")

    implementation("org.apache.kafka:kafka_2.12:${Versions.kafkaVersion}")
    implementation("org.apache.avro:avro:${Versions.avroVersion}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluentVersion}")
    implementation("io.confluent:kafka-schema-registry:${Versions.confluentVersion}")
    implementation("no.nav.syfo.oppfolgingsplan.avro:syfoopservice-schema:${Versions.syfoOppfolgingsplanSchemaVersion}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgresVersion}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariVersion}")
    implementation("org.flywaydb:flyway-core:${Versions.flywayVersion}")
    implementation("com.bettercloud:vault-java-driver:${Versions.vaultJavaDriveVersion}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbeddedVersion}")

    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluentVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spekVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwtVersion}")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:${Versions.spekVersion}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spekVersion}")
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.MainApplicationKt"
    }

    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
