package no.nav.syfo.testutil

import no.nav.syfo.*
import java.net.ServerSocket
import java.util.*

fun testEnvironment(
    kafkaBootstrapServers: String,
    behandlendeEnhetUrl: String = "behandlendeenhet",
    stsRestUrl: String = "stsurl",
    syfotilgangskontrollUrl: String = "tilgangskontroll",
) = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",
    aadDiscoveryUrl = "",
    loginserviceClientId = "",
    jwkKeysUrl = "",
    jwtIssuer = "",
    kafkaBootstrapServers = kafkaBootstrapServers,
    databaseName = "ispersonoppgave",
    ispersonoppgaveDBURL = "12314.adeo.no",
    mountPathVault = "vault.adeo.no",
    behandlendeenhetUrl = behandlendeEnhetUrl,
    stsRestUrl = stsRestUrl,
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
