package no.nav.syfo.testutil.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.Environment
import no.nav.syfo.common.mock.tilgangskontroll.mockTilgangskontrollRequestHandler
import no.nav.syfo.common.mock.token.azuread.mockAzureAdRequestHandler
import no.nav.syfo.infrastructure.clients.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azureAdClient.openidConfigTokenEndpoint}" -> mockAzureAdRequestHandler(request)
                requestUrl.startsWith("/${environment.istilgangskontrollClient.baseUrl}") -> mockTilgangskontrollRequestHandler(
                    request,
                    mockTilgangDetailsPerNavident
                )

                requestUrl.startsWith("/${environment.pdlUrl}") -> pdlMockResponse(request)

                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
