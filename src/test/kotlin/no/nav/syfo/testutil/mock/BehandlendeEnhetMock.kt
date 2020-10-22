package no.nav.syfo.testutil.mock

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.client.enhet.BehandlendeEnhet
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_2_FNR
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
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
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
