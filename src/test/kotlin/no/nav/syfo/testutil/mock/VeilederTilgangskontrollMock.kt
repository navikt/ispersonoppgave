package no.nav.syfo.testutil.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER

fun MockRequestHandleScope.tilgangskontrollMockResponse(request: HttpRequestData): HttpResponseData {
    return when (request.headers[NAV_PERSONIDENT_HEADER]) {
        ARBEIDSTAKER_FNR.value -> respond(Tilgang(erGodkjent = true))
        else -> respond(Tilgang(erGodkjent = false))
    }
}
