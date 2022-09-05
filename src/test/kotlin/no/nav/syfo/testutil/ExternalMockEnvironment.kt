package no.nav.syfo.testutil

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment(
    withSchemaRegistry: Boolean = false,
) {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()
    val embeddedEnvironment: KafkaEnvironment = testKafka(
        withSchemaRegistry = withSchemaRegistry,
    )

    val azureAdV2Mock = AzureAdV2Mock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()

    val externalApplicationMockMap = hashMapOf(
        azureAdV2Mock.name to azureAdV2Mock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        azureTokenEndpoint = azureAdV2Mock.url,
        syfotilgangskontrollUrl = tilgangskontrollMock.url
    )

    val wellKnownInternADV2Mock = wellKnownInternADV2Mock()
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.externalApplicationMockMap.start()
    this.embeddedEnvironment.start()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.externalApplicationMockMap.stop()
    this.database.stop()
    this.embeddedEnvironment.tearDown()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}

fun HashMap<String, NettyApplicationEngine>.stop(
    gracePeriodMillis: Long = 1L,
    timeoutMillis: Long = 10L
) {
    this.forEach {
        it.value.stop(gracePeriodMillis, timeoutMillis)
    }
}
