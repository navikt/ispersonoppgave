package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.token.azuread.AzureAdClient
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    val mockHttpClient = externalMockEnvironment.mockHttpClient

    val database = externalMockEnvironment.database

    val azureAdClient = AzureAdClient(
        config = externalMockEnvironment.environment.azureAdClient,
        httpClient = mockHttpClient,
    )

    val tilgangskontrollClient = TilgangskontrollClient(
        oboTokenProvider = azureAdClient,
        clientConfig = externalMockEnvironment.environment.istilgangskontrollClient,
        httpClient = mockHttpClient
    )

    val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        personoppgaveRepository = PersonOppgaveRepository(database = database),
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        tilgangskontrollClient = tilgangskontrollClient,
        database = database,
        environment = externalMockEnvironment.environment,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
        personOppgaveService = personOppgaveService,
    )
}
