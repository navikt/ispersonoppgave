package no.nav.syfo.testutil

import no.nav.syfo.*
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
    behandlendeEnhetUrl: String = "behandlendeenhet",
    syfotilgangskontrollUrl: String = "tilgangskontroll",
) = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",
    azureAppClientId = "app-client-id",
    azureAppClientSecret = "app-secret",
    azureAppWellKnownUrl = "wellknownurl",
    azureTokenEndpoint = azureTokenEndpoint,
    kafkaBootstrapServers = kafkaBootstrapServers,
    databaseName = "ispersonoppgave",
    ispersonoppgaveDBURL = "12314.adeo.no",
    mountPathVault = "vault.adeo.no",
    syfobehandlendeenhetClientId = "dev-fss:teamsykefravr:syfobehandlendeenhet",
    behandlendeenhetUrl = behandlendeEnhetUrl,
    syfotilgangskontrollClientId = syfotilgangskontrollUrl,
    syfotilgangskontrollUrl = syfotilgangskontrollUrl,
)

val vaultSecrets = VaultSecrets(
    "",
    "",
)

fun Properties.overrideForTest(): Properties = apply {
    remove("security.protocol")
    remove("sasl.mechanism")
}

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
