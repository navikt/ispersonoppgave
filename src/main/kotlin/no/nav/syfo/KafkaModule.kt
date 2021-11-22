package no.nav.syfo

import kotlinx.coroutines.*
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.retry.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.blockingApplicationLogicOppfolgingsplanLPS

fun launchKafkaTasks(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
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

    launchBackgroundTask(applicationState) {
        blockingApplicationLogicOppfolgingsplanLPS(
            applicationState = applicationState,
            environment = environment,
            vaultSecrets = vaultSecrets,
            oppfolgingsplanLPSService = oppfolgingsplanLPSService,
        )
    }

    launchBackgroundTask(applicationState) {
        blockingApplicationLogicOversikthendelseRetry(
            applicationState = applicationState,
            environment = environment,
            vaultSecrets = vaultSecrets,
            oversikthendelseRetryService = oversikthendelseRetryService,
        )
    }
}

fun launchBackgroundTask(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job = GlobalScope.launch {
    try {
        action()
    } catch (ex: Exception) {
        log.error("Exception received while launching background task. Terminating application.", ex)
    } finally {
        applicationState.alive = false
        applicationState.ready = false
    }
}
