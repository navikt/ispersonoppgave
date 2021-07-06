package no.nav.syfo

data class Environment(
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "1").toInt(),
    val applicationName: String = getEnvVar("APPLICATION_NAME", "ispersonoppgave"),
    val aadDiscoveryUrl: String = getEnvVar("AADDISCOVERY_URL"),
    val loginserviceClientId: String = getEnvVar("LOGINSERVICE_CLIENT_ID"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "ispersonoppgave"),
    val ispersonoppgaveDBURL: String = getEnvVar("ISPERSONOPPGAVE_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val stsRestUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_REST_URL"),
    val behandlendeenhetUrl: String = getEnvVar("SYFOBEHANDLENDEENHET_URL"),
    val syfotilgangskontrollUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
