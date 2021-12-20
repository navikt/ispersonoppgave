package no.nav.syfo.testutil

import no.nav.syfo.*
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
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
    ispersonoppgaveDbHost = "localhost",
    ispersonoppgaveDbPort = "5432",
    ispersonoppgaveDbName = "ispersonoppgave_dev",
    ispersonoppgaveDbUsername = "username",
    ispersonoppgaveDbPassword = "password",
    kafkaBootstrapServers = kafkaBootstrapServers,
    kafkaSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
    serviceuserUsername = "",
    serviceuserPassword = "",
    syfobehandlendeenhetClientId = "dev-fss:teamsykefravr:syfobehandlendeenhet",
    behandlendeenhetUrl = behandlendeEnhetUrl,
    syfotilgangskontrollClientId = syfotilgangskontrollUrl,
    syfotilgangskontrollUrl = syfotilgangskontrollUrl,
    toggleKafkaConsumerEnabled = true,
)

fun Properties.overrideForTest(): Properties = apply {
    remove(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG)
    remove(SaslConfigs.SASL_MECHANISM)
}

fun testAppState() = ApplicationState(
    alive = true,
    ready = true
)

fun getRandomPort() = ServerSocket(0).use {
    it.localPort
}
