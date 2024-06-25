package no.nav.syfo.testutil

import io.ktor.server.netty.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.mock.mockHttpClient
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()

    val environment = testEnvironment()

    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternADV2Mock = wellKnownInternADV2Mock()
}

fun ExternalMockEnvironment.stopExternalMocks() {
    this.database.stop()
}

fun HashMap<String, NettyApplicationEngine>.start() {
    this.forEach {
        it.value.start()
    }
}
