package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.oversikthendelse.OversikthendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    oversikthendelseProducer: OversikthendelseProducer,
) {
    val azureAdClient = AzureAdV2Client(
        azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
        azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
        azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
    )

    val behandlendeEnhetClient = BehandlendeEnhetClient(
        azureAdClient = azureAdClient,
        baseUrl = externalMockEnvironment.environment.behandlendeenhetUrl,
        syfobehandlendeenhetClientId = externalMockEnvironment.environment.syfobehandlendeenhetClientId,
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        behandlendeEnhetClient = behandlendeEnhetClient,
        oversikthendelseProducer = oversikthendelseProducer,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
    )
}
