package no.nav.syfo.testutil

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import java.net.ServerSocket
import java.util.*

fun testEnvironment(kafkaBootstrapServers: String) = Environment(
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
    behandlendeenhetUrl = "behandlendeenhet",
    stsRestUrl = "stsurl",
    syfotilgangskontrollUrl = "tilgangskontroll",
)

val vaultSecrets = VaultSecrets(
    "",
    "",
)

fun Properties.overrideForTest(): Properties = apply {
    remove("security.protocol")
    remove("sasl.mechanism")
}

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
