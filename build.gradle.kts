import com.adarshr.gradle.testlogger.theme.ThemeType

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

val confluentVersion = "8.1.0"
val jacksonDataTypeVersion = "2.20.0"
val flywayVersion = "11.17.1"
val hikariVersion = "7.0.2"
val isdialogmoteSchemaVersion = "1.0.5"
val jsonVersion = "20250517"
val kafkaVersion = "4.1.0"
val ktorVersion = "3.3.3"
val logbackVersion = "1.5.21"
val logstashEncoderVersion = "9.0"
val micrometerRegistryVersion = "1.12.13"
val mockkVersion = "1.14.6"
val nimbusjosejwtVersion = "10.6"
val joseVersion = "0.9.6"
val postgresVersion = "42.7.8"
val postgresEmbeddedVersion = "2.1.1"
val postgresRuntimeVersion = "17.6.0"

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.8"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.adarshr.test-logger") version "4.0.0"
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

    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("org.json:json:$jsonVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryVersion")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataTypeVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:$postgresRuntimeVersion"))

    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
        exclude(group = "org.apache.logging.log4j")
    }
    // Kafka
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://github.com/advisories/GHSA-6qvw-249j-h44c")
            version {
                require(joseVersion)
            }
        }
        implementation("commons-beanutils:commons-beanutils") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://www.cve.org/CVERecord?id=CVE-2025-48734")
            version {
                require("1.11.0")
            }
        }
    }
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    constraints {
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.28.0")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:$confluentVersion", excludeLog4j)
    constraints {
        implementation("io.github.classgraph:classgraph") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2021-47621")
            version {
                require("4.8.179")
            }
        }
        implementation("org.json:json") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-5072")
            version {
                require(jsonVersion)
            }
        }
        implementation("org.glassfish.jersey.core:jersey-client") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2025-12383")
            version {
                require("3.1.11")
            }
        }
        implementation("com.nimbusds:nimbus-jose-jwt") {
            version {
                require(nimbusjosejwtVersion)
            }
        }
    }
	
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:$isdialogmoteSchemaVersion")

    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusjosejwtVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.MainApplicationKt"
    }

    create("printVersion") {
        doLast { println(project.version) }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform()
        testlogger {
            theme = ThemeType.STANDARD_PARALLEL
            showFullStackTraces = true
            showPassed = false
        }
    }
}
