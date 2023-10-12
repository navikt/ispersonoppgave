package no.nav.syfo.testutil

import no.nav.syfo.*
import java.time.LocalDate

fun testEnvironment(
    kafkaBootstrapServers: String,
) = Environment(
    applicationThreads = 1,
    applicationName = "ispersonoppgave",
    azureAppClientId = "app-client-id",
    azureAppClientSecret = "app-secret",
    azureAppWellKnownUrl = "wellknownurl",
    azureTokenEndpoint = "azureTokenEndpoint",
    ispersonoppgaveDbHost = "localhost",
    ispersonoppgaveDbPort = "5432",
    ispersonoppgaveDbName = "ispersonoppgave_dev",
    ispersonoppgaveDbUsername = "username",
    ispersonoppgaveDbPassword = "password",
    pdlClientId = "pdlClientId",
    pdlUrl = "pdlUrl",
    serviceuserUsername = "",
    serviceuserPassword = "",
    syfotilgangskontrollClientId = "syfotilgangskontrollClientId",
    syfotilgangskontrollUrl = "syfotilgangskontrollUrl",
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
    outdatedDialogmotesvarCutoff = LocalDate.parse("2022-04-01"),
    kakfaConsumerAktivitetskravExpiredVarselEnabled = true,
    isAktivitetskravVurderingConsumerEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)
