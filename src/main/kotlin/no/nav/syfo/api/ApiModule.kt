package no.nav.syfo.api

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.authentication.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.api.v2.registerVeilederPersonOppgaveApiV2
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
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

    val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
    )
    val azureAdV2Client = AzureAdV2Client(
        azureAppClientId = environment.azureAppClientId,
        azureAppClientSecret = environment.azureAppClientSecret,
        azureTokenEndpoint = environment.azureTokenEndpoint,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdV2Client = azureAdV2Client,
        syfotilgangskontrollClientId = environment.syfotilgangskontrollClientId,
        endpointUrl = environment.syfotilgangskontrollUrl,
    )

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
