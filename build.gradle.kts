import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

object Versions {
    const val avroVersion = "1.10.0"
    const val confluentVersion = "6.1.3"
    const val jacksonVersion = "2.13.1"
    const val flywayVersion = "8.4.3"
    const val hikariVersion = "5.0.1"
    const val kafkaVersion = "2.7.0"
    const val kafkaEmbeddedVersion = "2.7.0"
    const val kluentVersion = "1.68"
    const val ktorVersion = "1.6.7"
    const val logbackVersion = "1.2.10"
    const val logstashEncoderVersion = "7.0.1"
    const val mockkVersion = "1.12.2"
    const val nimbusjosejwtVersion = "9.18"
    const val postgresVersion = "42.3.1"
    const val postgresEmbeddedVersion = "0.13.4"
    const val prometheusVersion = "0.9.0"
    const val scala = "2.13.7"
    const val spekVersion = "2.0.17"
    const val syfoOppfolgingsplanSchemaVersion = "1.0.2"
}

plugins {
    kotlin("jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://repository.mulesoft.org/nexus/content/repositories/public/")
    maven(url = "https://jitpack.io")
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

    implementation("io.ktor:ktor-auth-jwt:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorVersion}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Versions.logbackVersion}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoderVersion}")

    // Metrics
    implementation("io.prometheus:simpleclient_common:${Versions.prometheusVersion}")
    implementation("io.prometheus:simpleclient_hotspot:${Versions.prometheusVersion}")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVersion}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgresVersion}")
    implementation("org.flywaydb:flyway-core:${Versions.flywayVersion}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariVersion}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbeddedVersion}")

    // Kafka
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafkaVersion}")
    implementation("org.apache.avro:avro:${Versions.avroVersion}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluentVersion}")
    implementation("io.confluent:kafka-schema-registry:${Versions.confluentVersion}")
    implementation("no.nav.syfo.oppfolgingsplan.avro:syfoopservice-schema:${Versions.syfoOppfolgingsplanSchemaVersion}")
    implementation("org.scala-lang:scala-library") {
        version {
            strictly(Versions.scala)
        }
    }
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion}")

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwtVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluentVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spekVersion}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spekVersion}") {
        exclude(group = "org.jetbrains.kotlin")
    }
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
