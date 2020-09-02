package no.nav.syfo

import io.ktor.application.Application
import kotlinx.coroutines.launch
import no.nav.syfo.kafka.setupKafka
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService

fun Application.kafkaModule(
    vaultSecrets: VaultSecrets,
    oversikthendelseProducer: OversikthendelseProducer
) {
    val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
        database,
        oversikthendelseProducer
    )
    var toggleProcessing = false
    isDev {
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
