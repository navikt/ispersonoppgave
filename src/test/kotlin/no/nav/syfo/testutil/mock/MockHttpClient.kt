package no.nav.syfo.testutil.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.Environment
import no.nav.syfo.client.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azureTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.syfotilgangskontrollUrl}") -> tilgangskontrollMockResponse(
                    request
                )

                requestUrl.startsWith("/${environment.pdlUrl}") -> pdlMockResponse(request)

                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
