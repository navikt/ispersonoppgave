package no.nav.syfo

data class Environment(
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "ispersonoppgave"),

    val azureAppClientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val azureAppClientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val azureAppWellKnownUrl: String = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
    val azureTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),

    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "ispersonoppgave"),
    val ispersonoppgaveDBURL: String = getEnvVar("ISPERSONOPPGAVE_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val syfobehandlendeenhetClientId: String = getEnvVar("SYFOBEHANDLENDEENHET_CLIENT_ID"),
    val behandlendeenhetUrl: String = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
