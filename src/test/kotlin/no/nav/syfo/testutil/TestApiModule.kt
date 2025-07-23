package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.personoppgave.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    val mockHttpClient = externalMockEnvironment.mockHttpClient
    val database = externalMockEnvironment.database
    val azureAdClient = AzureAdClient(
        azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
        azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
        azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
        httpClient = mockHttpClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        istilgangskontrollClientId = externalMockEnvironment.environment.istilgangskontrollClientId,
        endpointUrl = externalMockEnvironment.environment.istilgangskontrollUrl,
        httpClient = mockHttpClient
    )
    val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        personoppgaveRepository = PersonOppgaveRepository(database = database),
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        database = database,
        environment = externalMockEnvironment.environment,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
        personOppgaveService = personOppgaveService,
    )
}
