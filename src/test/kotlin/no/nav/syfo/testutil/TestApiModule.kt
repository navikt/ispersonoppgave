package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    val mockHttpClient = externalMockEnvironment.mockHttpClient
    val azureAdClient = AzureAdClient(
        azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
        azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
        azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
        httpClient = mockHttpClient,
    )
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = azureAdClient,
        syfotilgangskontrollClientId = externalMockEnvironment.environment.syfotilgangskontrollClientId,
        endpointUrl = externalMockEnvironment.environment.syfotilgangskontrollUrl,
        httpClient = mockHttpClient
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
    )
}
