package no.nav.syfo.testutil

import io.ktor.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.client.sts.StsRestClient
import no.nav.syfo.oversikthendelse.OversikthendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    oversikthendelseProducer: OversikthendelseProducer,
) {
    val stsClientRest = StsRestClient(
        externalMockEnvironment.environment.stsRestUrl,
        externalMockEnvironment.vaultSecrets.serviceuserUsername,
        externalMockEnvironment.vaultSecrets.serviceuserPassword,
    )
    val behandlendeEnhetClient = BehandlendeEnhetClient(
        externalMockEnvironment.environment.behandlendeenhetUrl,
        stsClientRest,
    )

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        behandlendeEnhetClient = behandlendeEnhetClient,
        oversikthendelseProducer = oversikthendelseProducer,
    )
}
