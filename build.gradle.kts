import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

val confluentVersion = "7.6.0"
val jacksonDataTypeVersion = "2.17.1"
val flywayVersion = "10.15.0"
val hikariVersion = "5.1.0"
val isdialogmoteSchemaVersion = "1.0.5"
val jsonVersion = "20240303"
val jettyVersion = "9.4.54.v20240208"
val kafkaVersion = "3.7.0"
val kluentVersion = "1.73"
val ktorVersion = "2.3.11"
val logbackVersion = "1.4.14"
val logstashEncoderVersion = "7.4"
val micrometerRegistryVersion = "1.12.6"
val mockkVersion = "1.13.9"
val nimbusjosejwtVersion = "9.39.1"
val joseVersion = "0.9.4"
val postgresVersion = "42.7.3"
val postgresEmbeddedVersion = "2.0.7"
val spekVersion = "2.0.19"

plugins {
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.2"
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
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")

    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    // Kafka
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.bitbucket.b_c:jose4j:0.9.3 -> https://ossindex.sonatype.org/vulnerability/CVE-2023-51775")
            version {
                require("$joseVersion")
            }
        }
    }
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.11.3")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.26.0")
            }
        }
        implementation("com.google.guava:guava") {
            because("com.google.guava:guava:30.1.1-jre -> https://www.cve.org/CVERecord?id=CVE-2020-8908")
            version {
                require("32.1.3-jre")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:$confluentVersion", excludeLog4j)
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.9.2")
            }
        }
        implementation("com.google.protobuf:protobuf-java") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2021-22569")
            version {
                require("3.25.1")
            }
        }
        implementation("org.eclipse.jetty:jetty-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-xml") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-servlets") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
    }
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:$isdialogmoteSchemaVersion")

    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusjosejwtVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(21)
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
