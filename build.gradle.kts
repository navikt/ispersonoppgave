import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.taskdefs.condition.Os

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

object Versions {
    const val avro = "1.10.0"
    const val confluent = "6.2.2"
    const val jacksonDataType = "2.14.2"
    const val flyway = "8.5.13"
    const val hikari = "5.0.1"
    const val isdialogmoteSchema = "1.0.5"
    const val kafka = "2.8.1"
    const val kafkaEmbedded = "2.8.1"
    const val kluent = "1.72"
    const val ktor = "2.2.4"
    const val logback = "1.4.5"
    const val logstashEncoder = "7.2"
    const val micrometerRegistry = "1.10.3"
    const val mockk = "1.13.3"
    const val nimbusjosejwt = "9.30"
    const val postgres = "42.5.1"
    val postgresEmbedded = if (Os.isFamily(Os.FAMILY_MAC)) "1.0.0" else "0.13.4"
    const val scala = "2.13.9"
    const val spek = "2.0.19"
}

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.0"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://repository.mulesoft.org/nexus/content/repositories/public/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/isdialogmote-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-server-call-id:${Versions.ktor}")
    implementation("io.ktor:ktor-server-status-pages:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktor}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:${Versions.ktor}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonDataType}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgres}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbedded}")

    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    // Kafka
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafka}", excludeLog4j)
    implementation("org.apache.avro:avro:${Versions.avro}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}")
    implementation("io.confluent:kafka-schema-registry:${Versions.confluent}", excludeLog4j)
    constraints {
        implementation("org.yaml:snakeyaml") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2022-38752/")
            version {
                require("1.33")
            }
        }
        implementation("org.glassfish:jakarta.el") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2021-28170/")
            version {
                require("3.0.4")
            }
        }
        implementation("com.google.protobuf:protobuf-java") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2021-22569")
            version {
                require("3.21.7")
            }
        }
        implementation("com.google.code.gson:gson") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2022-25647")
            version {
                require("2.8.9")
            }
        }
    }
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema}")
    implementation("org.scala-lang:scala-library") {
        version {
            strictly(Versions.scala)
        }
    }
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbedded}", excludeLog4j)

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwt}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(17)
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

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
