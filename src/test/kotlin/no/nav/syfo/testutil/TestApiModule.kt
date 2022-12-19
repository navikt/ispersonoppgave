package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    val azureAdV2Client = AzureAdV2Client(
        azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
        azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
        azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        azureAdV2Client = azureAdV2Client,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
    )
}
