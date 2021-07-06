package no.nav.syfo.oversikthendelse.retry

import no.nav.syfo.client.enhet.BehandlendeEnhet
import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.COUNT_OVERSIKTHENDELSE_RETRY_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SKIPPED
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.personoppgave.updatePersonOppgaveOversikthendelse
import org.slf4j.LoggerFactory
import java.util.*

class OversikthendelseRetryService(
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val database: DatabaseInterface,
    private val oversikthendelseProducer: OversikthendelseProducer,
    private val oversikthendelseRetryProducer: OversikthendelseRetryProducer,
) {
    suspend fun receiveOversikthendelseRetry(
        kOversikthendelseRetry: KOversikthendelseRetry,
        callId: String = "",
    ) {
        when {
            kOversikthendelseRetry.hasExceededRetryLimit() -> {
                skip(kOversikthendelseRetry)
            }
            kOversikthendelseRetry.isReadyToRetry() -> {
                val fodselsnummer = Fodselsnummer(kOversikthendelseRetry.fnr)

                val behandlendeEnhet = behandlendeEnhetClient.getEnhet(fodselsnummer, callId)
                if (behandlendeEnhet != null) {
                    resendOversikthendelseAndUpdatePersonOppgave(
                        fodselsnummer,
                        kOversikthendelseRetry,
                        behandlendeEnhet,
                        callId,
                    )
                } else {
                    oversikthendelseRetryProducer.sendRetriedOversikthendelseRetry(
                        kOversikthendelseRetry,
                        callId,
                    )
                }
            }
            else -> {
                oversikthendelseRetryProducer.sendAgainOversikthendelseRetry(
                    kOversikthendelseRetry,
                    callId,
                )
            }
        }
    }

    private fun resendOversikthendelseAndUpdatePersonOppgave(
        fodselsnummer: Fodselsnummer,
        kOversikthendelseRetry: KOversikthendelseRetry,
        behandlendeEnhet: BehandlendeEnhet,
        callId: String,
    ) {
        database.getPersonOppgaveList(fodselsnummer)
            .find { personOppgave ->
                personOppgave.id == kOversikthendelseRetry.personOppgaveId &&
                    personOppgave.oversikthendelseTidspunkt == null
            }?.let {
                oversikthendelseProducer.sendOversikthendelse(
                    UUID.fromString(kOversikthendelseRetry.personOppgaveUUID),
                    fodselsnummer,
                    behandlendeEnhet,
                    OversikthendelseType.valueOf(kOversikthendelseRetry.oversikthendelseType),
                    callId,
                )
                database.updatePersonOppgaveOversikthendelse(kOversikthendelseRetry.personOppgaveId)
            } ?: return
    }

    private fun skip(oversikthendelseRetry: KOversikthendelseRetry) {
        LOG.error("Retry limit $RETRY_OVERSIKTHENDELSE_COUNT_LIMIT reached, skipping oversikthendelseRetry with retryCounter=${oversikthendelseRetry.retriedCount}")
        if (oversikthendelseRetry.oversikthendelseType == OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name) {
            COUNT_OVERSIKTHENDELSE_RETRY_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SKIPPED.inc()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(OversikthendelseRetryService::class.java)
    }
}
