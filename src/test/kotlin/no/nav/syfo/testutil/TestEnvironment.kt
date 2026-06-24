package no.nav.syfo.testutil

import no.nav.syfo.*
import no.nav.syfo.common.token.azuread.AzureAdClientConfig
import no.nav.syfo.common.util.ClientConfig
import java.time.LocalDate

fun testEnvironment() = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",

    azureAdClient = AzureAdClientConfig(
        appClientId = "app-client-id",
        appClientSecret = "app-secret",
        appWellKnownUrl = "wellknownurl",
        openidConfigTokenEndpoint = "azureTokenEndpoint",
    ),

    istilgangskontrollClient = ClientConfig(
        baseUrl = "istilgangskontrollUrl",
        clientId = "istilgangskontrollClientId",
    ),

    ispersonoppgaveDbHost = "localhost",
    ispersonoppgaveDbPort = "5432",
    ispersonoppgaveDbName = "ispersonoppgave_dev",
    ispersonoppgaveDbUsername = "username",
    ispersonoppgaveDbPassword = "password",
    pdlClientId = "pdlClientId",
    pdlUrl = "pdlUrl",
    serviceuserUsername = "",
    serviceuserPassword = "",

    kafka = EnvironmentKafka(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
        aivenSecurityProtocol = "SSL",
        aivenCredstorePassword = "credstorepassord",
        aivenTruststoreLocation = "truststore",
        aivenKeystoreLocation = "keystore",
    ),
    electorPath = "electorPath",
    outdatedDialogmotesvarCutoff = LocalDate.parse("2022-04-01"),
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)
