package no.nav.syfo.testutil

import no.nav.syfo.*
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    azureTokenEndpoint: String = "azureTokenEndpoint",
    behandlendeEnhetUrl: String = "behandlendeenhet",
    stsRestUrl: String = "stsurl",
    syfotilgangskontrollUrl: String = "tilgangskontroll",
) = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",
    azureAppClientId = "app-client-id",
    azureAppClientSecret = "app-secret",
    azureAppWellKnownUrl = "wellknownurl",
    azureTokenEndpoint = azureTokenEndpoint,
    aadDiscoveryUrl = "",
    loginserviceClientId = "123456789",
    kafkaBootstrapServers = kafkaBootstrapServers,
    databaseName = "ispersonoppgave",
    ispersonoppgaveDBURL = "12314.adeo.no",
    mountPathVault = "vault.adeo.no",
    behandlendeenhetUrl = behandlendeEnhetUrl,
    stsRestUrl = stsRestUrl,
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
