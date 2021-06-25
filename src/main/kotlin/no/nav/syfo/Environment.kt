package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

const val defaultlocalEnvironmentPropertiesPath = "./src/main/resources/localEnvForTests.json"
private val objectMapper: ObjectMapper = ObjectMapper()

fun getEnvironment(): Environment {
    objectMapper.registerKotlinModule()
    return if (appIsRunningLocally) {
        objectMapper.readValue(firstExistingFile(defaultlocalEnvironmentPropertiesPath), Environment::class.java)
    } else {
        Environment(
            getEnvVar("APPLICATION_PORT", "8080").toInt(),
            getEnvVar("APPLICATION_THREADS", "1").toInt(),
            getEnvVar("APPLICATION_NAME", "ispersonoppgave"),
            getEnvVar("AADDISCOVERY_URL"),
            getEnvVar("LOGINSERVICE_CLIENT_ID"),
            getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
            getEnvVar("JWT_ISSUER"),
            getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
            getEnvVar("DATABASE_NAME", "ispersonoppgave"),
            getEnvVar("ISPERSONOPPGAVE_DB_URL"),
            getEnvVar("MOUNT_PATH_VAULT"),
            getEnvVar("SECURITY_TOKEN_SERVICE_REST_URL"),
            getEnvVar("SYFOBEHANDLENDEENHET_URL"),
            getEnvVar("SYFOTILGANGSKONTROLL_URL")
        )
    }
}

val appIsRunningLocally: Boolean = System.getenv("NAIS_CLUSTER_NAME").isNullOrEmpty()

data class Environment(
    val applicationPort: Int,
    val applicationThreads: Int,
    val applicationName: String,
    val aadDiscoveryUrl: String,
    val loginserviceClientId: String,
    val jwkKeysUrl: String,
    val jwtIssuer: String,
    val kafkaBootstrapServers: String,
    val databaseName: String,
    val ispersonoppgaveDBURL: String,
    val mountPathVault: String,
    val stsRestUrl: String,
    val behandlendeenhetUrl: String,
    val syfotilgangskontrollUrl: String
)

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

private fun firstExistingFile(vararg paths: String) = paths
    .map(::File)
    .first(File::exists)
