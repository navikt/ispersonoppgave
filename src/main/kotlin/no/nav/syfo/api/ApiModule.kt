package no.nav.syfo.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.authentication.*
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.registerVeilederPersonOppgaveApi

fun Application.apiModule(
    applicationState: ApplicationState,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    database: DatabaseInterface,
    environment: Environment,
    oversikthendelseProducer: OversikthendelseProducer,
    wellKnownInternADV1: WellKnown,
) {
    installCallId()
    installContentNegotiation()
    installJwtAuthentication(
        jwtIssuerList = listOf(
            JwtIssuer(
                accectedAudienceList = listOf(environment.loginserviceClientId),
                jwtIssuerType = JwtIssuerType.INTERN_AZUREAD_V1,
                wellKnown = wellKnownInternADV1,
            ),
        ),
    )
    installStatusPages()

    val personOppgaveService = PersonOppgaveService(
        database = database,
        behandlendeEnhetClient = behandlendeEnhetClient,
        oversikthendelseProducer = oversikthendelseProducer,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        endpointUrl = environment.syfotilgangskontrollUrl,
    )

    routing {
        registerPodApi(applicationState)
        registerPrometheusApi()
        authenticate(JwtIssuerType.INTERN_AZUREAD_V1.name) {
            registerVeilederPersonOppgaveApi(
                personOppgaveService,
                veilederTilgangskontrollClient
            )
        }
    }

    applicationState.ready = true
}
