package no.nav.syfo.testutil.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.api.installContentNegotiation
import no.nav.syfo.client.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.testutil.getRandomPort
import java.nio.file.Paths

fun wellKnownInternADV2Mock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        authorization_endpoint = "authorizationendpoint",
        token_endpoint = "tokenendpoint",
        jwks_uri = uri.toString(),
        issuer = "https://sts.issuer.net/v2"
    )
}

class AzureAdV2Mock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val aadV2TokenResponse = AzureAdV2TokenResponse(
        access_token = "token",
        expires_in = 3600,
        token_type = "type"
    )

    val name = "azureadv2"
    val server = embeddedServer(
        factory = Netty,
        port = port,
    ) {
        installContentNegotiation()
        routing {
            post {
                call.respond(aadV2TokenResponse)
            }
        }
    }
}
