package no.nav.syfo.testutil.mock

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.installContentNegotiation
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient.Companion.TILGANGSKONTROLL_V2_PERSON_PATH
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.getRandomPort

class VeilederTilgangskontrollMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val tilgangFalse = Tilgang(
        false,
        ""
    )
    val tilgangTrue = Tilgang(
        true,
        ""
    )

    val name = "veiledertilgangskontroll"
    val server = mockTilgangServer(
        port,
        tilgangFalse,
        tilgangTrue,
    )

    private fun mockTilgangServer(
        port: Int,
        tilgangFalse: Tilgang,
        tilgangTrue: Tilgang,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port,
        ) {
            installContentNegotiation()
            routing {
                get("$TILGANGSKONTROLL_V2_PERSON_PATH/${ARBEIDSTAKER_FNR.value}") {
                    call.respond(tilgangTrue)
                }
                get(TILGANGSKONTROLL_V2_PERSON_PATH) {
                    call.respond(HttpStatusCode.Forbidden, tilgangFalse)
                }
            }
        }
    }
}
