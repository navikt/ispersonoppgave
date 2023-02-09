package no.nav.syfo

import java.time.LocalDate

data class Environment(
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "ispersonoppgave"),

    val azureAppClientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val azureAppClientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val azureAppWellKnownUrl: String = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
    val azureTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),

    val ispersonoppgaveDbHost: String = getEnvVar("NAIS_DATABASE_ISPERSONOPPGAVE_ISPERSONOPPGAVE_DB_HOST"),
    val ispersonoppgaveDbPort: String = getEnvVar("NAIS_DATABASE_ISPERSONOPPGAVE_ISPERSONOPPGAVE_DB_PORT"),
    val ispersonoppgaveDbName: String = getEnvVar("NAIS_DATABASE_ISPERSONOPPGAVE_ISPERSONOPPGAVE_DB_DATABASE"),
    val ispersonoppgaveDbUsername: String = getEnvVar("NAIS_DATABASE_ISPERSONOPPGAVE_ISPERSONOPPGAVE_DB_USERNAME"),
    val ispersonoppgaveDbPassword: String = getEnvVar("NAIS_DATABASE_ISPERSONOPPGAVE_ISPERSONOPPGAVE_DB_PASSWORD"),

    val pdlUrl: String = getEnvVar("PDL_URL"),
    val pdlClientId: String = getEnvVar("PDL_CLIENT_ID"),

    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),

    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val kafka: EnvironmentKafka = EnvironmentKafka(
        aivenBootstrapServers = getEnvVar("KAFKA_BROKERS"),
        aivenSchemaRegistryUrl = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
        aivenRegistryUser = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
        aivenRegistryPassword = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
        aivenTruststoreLocation = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
        aivenKeystoreLocation = getEnvVar("KAFKA_KEYSTORE_PATH"),
    ),

    val toggleKafkaConsumerStatusendringEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_STATUSENDRING_CONSUMER_ENABLED").toBoolean(),
    val toggleKafkaConsumerDialogmotesvarEnabled: Boolean = getEnvVar("TOGGLE_KAFKA_DIALOGMOTESVAR_CONSUMER_ENABLED").toBoolean(),

    val electorPath: String = getEnvVar("ELECTOR_PATH"),

    val publishOppgavehendelser: Boolean = getEnvVar("PUBLISH_OPPGAVEHENDELSER_ENABLED").toBoolean(),

    val outdatedDialogmotesvarCutoff: LocalDate = LocalDate.parse(getEnvVar("OUTDATED_DIALOGMOTESVAR_CUTOFF")),
) {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$ispersonoppgaveDbHost:$ispersonoppgaveDbPort/$ispersonoppgaveDbName"
    }
}

data class EnvironmentKafka(
    val aivenBootstrapServers: String,
    val aivenSchemaRegistryUrl: String,
    val aivenRegistryUser: String,
    val aivenRegistryPassword: String,
    val aivenSecurityProtocol: String,
    val aivenCredstorePassword: String,
    val aivenTruststoreLocation: String,
    val aivenKeystoreLocation: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
