package no.nav.syfo.personoppgave.oppfolgingsplanlps

import no.nav.syfo.client.enhet.BehandlendeEnhetClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.oversikthendelse.OversikthendelseProducer
import no.nav.syfo.oversikthendelse.domain.OversikthendelseType
import no.nav.syfo.oversikthendelse.retry.OversikthendelseRetryProducer
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.util.callIdArgument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps")

class OppfolgingsplanLPSService(
    private val database: DatabaseInterface,
    private val behandlendeEnhetClient: BehandlendeEnhetClient,
    private val oversikthendelseProducer: OversikthendelseProducer,
    private val oversikthendelseRetryProducer: OversikthendelseRetryProducer
) {
    suspend fun receiveOppfolgingsplanLPS(
        kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV,
        callId: String = ""
    ) {
        if (kOppfolgingsplanLPSNAV.getBehovForBistandFraNav() == true) {
            val person: PPersonOppgave? = database.getPersonOppgaveList(Fodselsnummer(kOppfolgingsplanLPSNAV.getFodselsnummer()))
                .find { it.referanseUuid == UUID.fromString(kOppfolgingsplanLPSNAV.getUuid()) }
            if (person == null) {
                val idPair = database.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    PersonOppgaveType.OPPFOLGINGSPLANLPS
                )
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED.inc()

                val fodselsnummer = Fodselsnummer(kOppfolgingsplanLPSNAV.getFodselsnummer())
                val sent = sendOversikthendelse(idPair.second, fodselsnummer, callId)
                if (sent) {
                    database.updatePersonOppgaveOversikthendelse(idPair.first)
                    COUNT_OVERSIKTHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT.inc()
                } else {
                    log.warn("Failed to send Oversikthendelse for OppfolgingsplanLPS due to missing BehandlendeEnhet. Sending Retry message {}", callIdArgument(callId))
                    oversikthendelseRetryProducer.sendFirstOversikthendelseRetry(
                        fnr = fodselsnummer,
                        oversikthendelseType = OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT,
                        personOppgaveId = idPair.first,
                        personOppgaveUUID = idPair.second,
                        callId = callId
                    )
                    COUNT_OPPFOLGINGSPLANLPS_FIRST_OVERSIKTHENDELSE_RETRY.inc()
                }
            } else {
                log.error("Already create a PersonOppgave for OppfolgingsplanLPS with UUID {}, {}", kOppfolgingsplanLPSNAV.getUuid(), callIdArgument(callId))
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED.inc()
            }
        } else {
            log.info("OppfolgingsplanLPS does not have BehovForBistandFraNav=true and is skipped, {}", callIdArgument(callId))
            COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND.inc()
        }
    }

    suspend fun sendOversikthendelse(
        personOppgaveUUID: UUID,
        fodselsnummer: Fodselsnummer,
        callId: String = ""
    ): Boolean {
        val behandlendeEnhet = behandlendeEnhetClient.getEnhet(fodselsnummer, callId) ?: return false

        oversikthendelseProducer.sendOversikthendelse(
            personOppgaveUUID,
            fodselsnummer,
            behandlendeEnhet,
            OversikthendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT,
            callId
        )
        return true
    }
}
