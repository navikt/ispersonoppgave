package no.nav.syfo.testutil.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.client.azuread.AzureAdTokenResponse
import java.nio.file.Paths

fun MockRequestHandleScope.azureAdMockResponse(): HttpResponseData = respond(
    AzureAdTokenResponse(
        access_token = "token",
        expires_in = 3600,
        token_type = "type",
    )
)

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
