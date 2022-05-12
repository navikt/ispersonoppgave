package no.nav.syfo

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
            oppfolgingsplanLPSService = oppfolgingsplanLPSService,
        )
    }

    launchBackgroundTask(applicationState) {
        blockingApplicationLogicOversikthendelseRetry(
            applicationState = applicationState,
            environment = environment,
            oversikthendelseRetryService = oversikthendelseRetryService,
        )
    }
}
