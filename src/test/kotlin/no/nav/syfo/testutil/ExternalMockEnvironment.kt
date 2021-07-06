package no.nav.syfo.testutil

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.VaultSecrets
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()
    val embeddedEnvironment: KafkaEnvironment = testKafka()

    val behandlendeEnhetMock = BehandlendeEnhetMock()
    val stsRestMock = StsRestMock()
    val tilgangskontrollMock = VeilederTilgangskontrollMock()

    val externalApplicationMockMap = hashMapOf(
        behandlendeEnhetMock.name to behandlendeEnhetMock.server,
        stsRestMock.name to stsRestMock.server,
        tilgangskontrollMock.name to tilgangskontrollMock.server
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        behandlendeEnhetUrl = behandlendeEnhetMock.url,
        stsRestUrl = stsRestMock.url,
        syfotilgangskontrollUrl = tilgangskontrollMock.url
    )
    val vaultSecrets = VaultSecrets(
        "",
        "",
    )
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
