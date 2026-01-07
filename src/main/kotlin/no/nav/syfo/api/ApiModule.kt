package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.authentication.installCallId
import no.nav.syfo.api.authentication.installContentNegotiation
import no.nav.syfo.api.authentication.installMetrics
import no.nav.syfo.api.authentication.installStatusPages
import no.nav.syfo.api.v2.registerPodApi
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.METRICS_REGISTRY
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.api.authentication.JwtIssuer
import no.nav.syfo.api.authentication.JwtIssuerType
import no.nav.syfo.api.authentication.WellKnown
import no.nav.syfo.api.authentication.installJwtAuthentication
import no.nav.syfo.api.v2.registerVeilederPersonOppgaveApiV2

fun Application.apiModule(
    applicationState: ApplicationState,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    database: DatabaseInterface,
    environment: Environment,
    personOppgaveService: PersonOppgaveService,
    wellKnownInternADV2: WellKnown,
) {
    installCallId()
    installContentNegotiation()
    installMetrics()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azureAppClientId),
                jwtIssuerType = JwtIssuerType.INTERN_AZUREAD_V2,
                wellKnown = wellKnownInternADV2,
            ),
        ),
    )
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
        registerMetricApi()
        authenticate(JwtIssuerType.INTERN_AZUREAD_V2.name) {
            registerVeilederPersonOppgaveApiV2(
                personOppgaveService,
                veilederTilgangskontrollClient,
            )
        }
    }
}

fun Routing.registerMetricApi() {
    get("/prometheus") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
