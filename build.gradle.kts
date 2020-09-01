import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0-SNAPSHOT"

object Versions {
    const val arrowVersion = "0.9.0"
    const val avroVersion = "1.8.2"
    const val confluentVersion = "5.3.0"
    const val coroutinesVersion = "1.3.7"
    const val flywayVersion = "5.2.4"
    const val fuelVersion = "1.15.1"
    const val hikariVersion = "3.3.0"
    const val kafkaVersion = "2.0.0"
    const val kafkaEmbeddedVersion = "2.3.0"
    const val kluentVersion = "1.52"
    const val kotlinSerializationVersion = "0.9.0"
    const val ktorVersion = "1.3.2"
    const val logbackVersion = "1.2.3"
    const val logstashEncoderVersion = "5.1"
    const val postgresVersion = "42.2.5"
    const val postgresTestContainersVersion = "1.11.3"
    const val prometheusVersion = "0.8.1"
    const val vaultJavaDriveVersion = "3.1.0"
    const val spekVersion = "2.0.9"
    const val jacksonVersion = "2.9.9"
    const val mockkVersion = "1.10.0"
    const val orgJsonVersion = "20180813"
    const val syfoOppfolgingsplanSchemaVersion = "1.0.2"
}

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "no.nav.syfo.MainApplicationKt"
}

plugins {
    kotlin("jvm") version "1.3.72"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://dl.bintray.com/kotlin/kotlinx/")
    maven(url = "http://packages.confluent.io/maven/")
    maven(url = "https://oss.sonatype.org/content/groups/staging/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfoopservice-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

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
    implementation("no.nav.syfo.oppfolgingsplan.avro:syfoopservice-schema:${Versions.syfoOppfolgingsplanSchemaVersion}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgresVersion}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariVersion}")
    implementation("org.flywaydb:flyway-core:${Versions.flywayVersion}")
    implementation("com.bettercloud:vault-java-driver:${Versions.vaultJavaDriveVersion}")
    testImplementation("org.testcontainers:postgresql:${Versions.postgresTestContainersVersion}")

    implementation("io.arrow-kt:arrow-core-data:${Versions.arrowVersion}")
    implementation("com.github.kittinunf.fuel:fuel:${Versions.fuelVersion}")

    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluentVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spekVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:${Versions.spekVersion}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spekVersion}")

    api("io.ktor:ktor-client-mock:${Versions.ktorVersion}")
    api("io.ktor:ktor-client-mock-jvm:${Versions.ktorVersion}")
}

tasks {
    create("printVersion") {
        println(project.version)
    }

    withType<ShadowJar> {
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
