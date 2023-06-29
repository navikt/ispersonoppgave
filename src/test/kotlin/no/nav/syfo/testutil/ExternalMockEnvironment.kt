package no.nav.syfo.testutil

import io.ktor.server.netty.*
import no.nav.common.KafkaEnvironment
import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.mock.mockHttpClient
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment(
    withSchemaRegistry: Boolean = false,
) {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()
    val embeddedEnvironment: KafkaEnvironment = testKafka(
        withSchemaRegistry = withSchemaRegistry,
    )

    val environment = testEnvironment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
    )

    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternADV2Mock = wellKnownInternADV2Mock()
}

fun ExternalMockEnvironment.startExternalMocks() {
    this.embeddedEnvironment.start()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.database.stop()
    this.embeddedEnvironment.tearDown()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}
