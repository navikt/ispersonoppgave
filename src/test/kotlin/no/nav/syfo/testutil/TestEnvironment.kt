package no.nav.syfo.testutil

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets

fun testEnvironment(port: Int, kafkaBootstrapServers: String) = Environment(
    applicationPort = port,
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
    syfotilgangskontrollUrl = "tilgangskontroll"
)

val vaultSecrets = VaultSecrets(
    "",
    ""
)
