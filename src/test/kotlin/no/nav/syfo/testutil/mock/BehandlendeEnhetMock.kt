package no.nav.syfo.testutil.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.installContentNegotiation
import no.nav.syfo.client.enhet.BehandlendeEnhet
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_2_FNR
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.getRandomPort

class BehandlendeEnhetMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val behandlendeEnhet = generateBehandlendeEnhet()
    val server = mockBehandlendeEnhetServer(port, behandlendeEnhet)

    private fun mockBehandlendeEnhetServer(
        port: Int,
        behandlendeEnhet: BehandlendeEnhet
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get("/api/${ARBEIDSTAKER_FNR.value}") {
                    call.respond(behandlendeEnhet)
                }
                get("/api/${ARBEIDSTAKER_2_FNR.value}") {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    fun generateBehandlendeEnhet() =
        BehandlendeEnhet(
            enhetId = "1234",
            navn = UserConstants.NAV_ENHET
        )
}
