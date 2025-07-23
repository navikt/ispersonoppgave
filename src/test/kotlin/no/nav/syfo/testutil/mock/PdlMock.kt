package no.nav.syfo.testutil.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.testutil.UserConstants

fun PersonIdent.toHistoricalPersonIdent(): PersonIdent {
    val firstDigit = this.value[0].digitToInt()
    val newDigit = firstDigit + 4
    val dNummer = this.value.replace(
        firstDigit.toString(),
        newDigit.toString(),
    )
    return PersonIdent(dNummer)
}

fun generatePdlIdenterResponse(
    personIdentNumber: PersonIdent,
) = PdlIdenterResponse(
    data = PdlHentIdenter(
        hentIdenter = PdlIdenter(
            identer = listOf(
                PdlIdent(
                    ident = personIdentNumber.value,
                    historisk = false,
                    gruppe = IdentType.FOLKEREGISTERIDENT,
                ),
                PdlIdent(
                    ident = personIdentNumber.toHistoricalPersonIdent().value,
                    historisk = true,
                    gruppe = IdentType.FOLKEREGISTERIDENT,
                ),
            ),
        ),
    ),
    errors = null,
)

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
    return when (val personIdent = PersonIdent(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_3_FNR -> {
            respond(generatePdlIdenterResponse(PersonIdent("11111111111")))
        }

        UserConstants.FEILENDE_FNR -> {
            respond(HttpStatusCode.InternalServerError)
        }

        else -> {
            respond(generatePdlIdenterResponse(personIdent))
        }
    }
}
