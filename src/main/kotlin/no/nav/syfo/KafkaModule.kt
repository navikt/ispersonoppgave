package no.nav.syfo

import io.ktor.application.Application
import kotlinx.coroutines.launch
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.kafka.setupKafka
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService

fun Application.kafkaModule(
    vaultSecrets: VaultSecrets,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    oversikthendelseProducer: OversikthendelseProducer
) {
    val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
        database,
        behandlendeEnhetClient,
        oversikthendelseProducer
    )
    var toggleProcessing = false
    if (isPreProd()) {
        toggleProcessing = true
    }
    launch(backgroundTasksContext) {
        setupKafka(
            vaultSecrets,
            oppfolgingsplanLPSService,
            toggleProcessing
        )
    }
}
