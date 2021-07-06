package no.nav.syfo.testutil.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.installContentNegotiation
import no.nav.syfo.testutil.getRandomPort

class StsRestMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val server = mockStsRestServer(port)

    private fun mockStsRestServer(
        port: Int,
    ): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port,
        ) {
            installContentNegotiation()
            routing {
                get("/rest/v1/sts/token") {
                    val params = call.request.queryParameters
                    if (params["grant_type"].equals("client_credentials") && params["scope"].equals("openid")) {
                        call.respond(defaultToken)
                    }
                }
            }
        }
    }
}

private val defaultToken = OidcToken(
    access_token = "default access token",
    expires_in = 3600,
    token_type = "Bearer",
    unknown_type = "uknown",
)

data class OidcToken(
    val access_token: String,
    val expires_in: Long,
    val token_type: String,
    val unknown_type: String,
)
