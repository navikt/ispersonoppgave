package no.nav.syfo

import io.ktor.application.Application
import kotlinx.coroutines.launch
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.database.database
import no.nav.syfo.kafka.setupKafka
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryProducer
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService

fun Application.kafkaModule(
    vaultSecrets: VaultSecrets,
    behandlendeEnhetClient: BehandlendeEnhetClient,
    oversikthendelseProducer: OversikthendelseProducer,
    oversikthendelseRetryProducer: OversikthendelseRetryProducer,
) {
    val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
        database,
        behandlendeEnhetClient,
        oversikthendelseProducer,
        oversikthendelseRetryProducer,
    )
    val oversikthendelseRetryService = OversikthendelseRetryService(
        behandlendeEnhetClient,
        database,
        oversikthendelseProducer,
        oversikthendelseRetryProducer,
    )
    launch(backgroundTasksContext) {
        setupKafka(
            vaultSecrets,
            oppfolgingsplanLPSService,
            oversikthendelseRetryService,
        )
    }
}
