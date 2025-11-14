package no.nav.syfo.testutil

import no.nav.syfo.ApplicationState
import no.nav.syfo.testutil.mock.mockHttpClient
import no.nav.syfo.testutil.mock.*

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDB()
    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternADV2Mock = wellKnownInternADV2Mock()

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
