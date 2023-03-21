package no.nav.syfo.testutil

import no.nav.syfo.*
import java.net.ServerSocket
import java.time.LocalDate

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
    syfotilgangskontrollUrl: String = "tilgangskontroll",
    pdlUrl: String? = null,
) = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",
    azureAppClientId = "app-client-id",
    azureAppClientSecret = "app-secret",
    azureAppWellKnownUrl = "wellknownurl",
    azureTokenEndpoint = azureTokenEndpoint,
    ispersonoppgaveDbHost = "localhost",
    ispersonoppgaveDbPort = "5432",
    ispersonoppgaveDbName = "ispersonoppgave_dev",
    ispersonoppgaveDbUsername = "username",
    ispersonoppgaveDbPassword = "password",
    pdlClientId = "pdlClientId",
    pdlUrl = pdlUrl ?: "http://pdl",
    serviceuserUsername = "",
    serviceuserPassword = "",
    syfotilgangskontrollClientId = syfotilgangskontrollUrl,
    syfotilgangskontrollUrl = syfotilgangskontrollUrl,
    kafka = EnvironmentKafka(
        aivenBootstrapServers = kafkaBootstrapServers,
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    electorPath = "electorPath",
    outdatedDialogmotesvarCutoff = LocalDate.parse("2022-04-01")
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
