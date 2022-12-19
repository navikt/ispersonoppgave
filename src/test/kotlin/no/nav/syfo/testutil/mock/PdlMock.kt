package no.nav.syfo.testutil.mock

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.installContentNegotiation
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.getRandomPort

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

class PdlMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "pdl"
    val server = embeddedServer(
        factory = Netty,
        port = port
    ) {
        installContentNegotiation()
        routing {
            post {
                val pdlRequest = call.receive<PdlHentIdenterRequest>()
                when (val personIdentNumber = PersonIdent(pdlRequest.variables.ident)) {
                    UserConstants.ARBEIDSTAKER_3_FNR -> {
                        call.respond(generatePdlIdenterResponse(PersonIdent("11111111111")))
                    }
                    UserConstants.FEILENDE_FNR -> {
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                    else -> {
                        call.respond(generatePdlIdenterResponse(personIdentNumber))
                    }
                }
            }
        }
    }
}
