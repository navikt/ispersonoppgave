package no.nav.syfo.kafka

import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.*
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.blockingApplicationLogicOppfolgingsplanLPS
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.createListenerOppfolgingsplanLPS

suspend fun CoroutineScope.setupKafka(
    applicationState: ApplicationState,
    environment: Environment,
    vaultSecrets: VaultSecrets,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    oversikthendelseRetryService: OversikthendelseRetryService,
) {
    createListenerOppfolgingsplanLPS(applicationState) {
        blockingApplicationLogicOppfolgingsplanLPS(
            applicationState = applicationState,
            environment = environment,
            vaultSecrets = vaultSecrets,
            oppfolgingsplanLPSService = oppfolgingsplanLPSService,
        )
    }

    createListenerOversikthendelseRetry(applicationState) {
        blockingApplicationLogicOversikthendelseRetry(
            applicationState = applicationState,
            environment = environment,
            vaultSecrets = vaultSecrets,
            oversikthendelseRetryService = oversikthendelseRetryService,
        )
    }
    applicationState.initialized = true
}
