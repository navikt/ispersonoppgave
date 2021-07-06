package no.nav.syfo.kafka

import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.*
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.blockingApplicationLogicOppfolgingsplanLPS
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.createListenerOppfolgingsplanLPS

suspend fun CoroutineScope.setupKafka(
    vaultSecrets: VaultSecrets,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    oversikthendelseRetryService: OversikthendelseRetryService,
) {
    createListenerOppfolgingsplanLPS(state) {
        blockingApplicationLogicOppfolgingsplanLPS(
            state,
            env,
            vaultSecrets,
            oppfolgingsplanLPSService,
        )
    }

    createListenerOversikthendelseRetry(state) {
        blockingApplicationLogicOversikthendelseRetry(
            state,
            env,
            vaultSecrets,
            oversikthendelseRetryService,
        )
    }
    state.initialized = true
}
