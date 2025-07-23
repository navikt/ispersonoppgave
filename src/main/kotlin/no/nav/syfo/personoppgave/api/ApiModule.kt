package no.nav.syfo.personoppgave.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.personoppgave.api.authentication.installCallId
import no.nav.syfo.personoppgave.api.authentication.installContentNegotiation
import no.nav.syfo.personoppgave.api.authentication.installMetrics
import no.nav.syfo.personoppgave.api.authentication.installStatusPages
import no.nav.syfo.personoppgave.api.v2.registerPodApi
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.metric.METRICS_REGISTRY
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.authentication.JwtIssuer
import no.nav.syfo.personoppgave.api.authentication.JwtIssuerType
import no.nav.syfo.personoppgave.api.authentication.WellKnown
import no.nav.syfo.personoppgave.api.authentication.installJwtAuthentication
import no.nav.syfo.personoppgave.api.v2.registerVeilederPersonOppgaveApiV2

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
