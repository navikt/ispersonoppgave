package no.nav.syfo.kafka

import kotlinx.coroutines.CoroutineScope
import no.nav.syfo.*
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryService
import no.nav.syfo.oversikthendelse.retry.launchListenerOversikthendelseRetry
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.launchListenerOppfolgingsplanLPS
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val LOG: Logger = LoggerFactory.getLogger("no.nav.syfo.Kafka")

suspend fun CoroutineScope.setupKafka(
    vaultSecrets: VaultSecrets,
    oppfolgingsplanLPSService: OppfolgingsplanLPSService,
    oversikthendelseRetryService: OversikthendelseRetryService,
) {
    LOG.info("Setting up kafka consumer OppfolgingsplanLPS")

    launchListenerOppfolgingsplanLPS(
        state,
        kafkaConsumerConfig(env, vaultSecrets),
        oppfolgingsplanLPSService
    )

    LOG.info("Setting up kafka consumer OversikthendelseRetry")

    launchListenerOversikthendelseRetry(
        state,
        kafkaConsumerOversikthendelseRetryProperties(env, vaultSecrets),
        oversikthendelseRetryService
    )

    state.initialized = true
}
