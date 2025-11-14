package no.nav.syfo.testutil

import io.ktor.server.netty.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.mock.mockHttpClient
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()
    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternADV2Mock = wellKnownInternADV2Mock()

    companion object {
        val instance: ExternalMockEnvironment by lazy { ExternalMockEnvironment() }
        fun newInstance(): ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
