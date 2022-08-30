package no.nav.syfo.personoppgave.oppfolgingsplanlps

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.metric.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.util.callIdArgument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps")

class OppfolgingsplanLPSService(
    private val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    fun receiveOppfolgingsplanLPS(
        kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV,
        callId: String = ""
    ) {
        if (kOppfolgingsplanLPSNAV.getBehovForBistandFraNav() == true) {
            val person: PPersonOppgave? = database.getPersonOppgaveList(PersonIdentNumber(kOppfolgingsplanLPSNAV.getFodselsnummer()))
                .find { it.referanseUuid == UUID.fromString(kOppfolgingsplanLPSNAV.getUuid()) }
            if (person == null) {
                log.info("Didn't find person with oppgave based on given referanseUuid: ${kOppfolgingsplanLPSNAV.getUuid()} creating new Personoppgave")
                val idPair = database.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    PersonOppgaveType.OPPFOLGINGSPLANLPS
                )
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED.increment()

                val fodselsnummer = PersonIdentNumber(kOppfolgingsplanLPSNAV.getFodselsnummer())
                sendPersonoppgavehendelse(idPair.second, fodselsnummer)
                database.updatePersonOppgaveOversikthendelse(idPair.first)
                COUNT_PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT.increment()
            } else {
                log.error("Already create a PersonOppgave for OppfolgingsplanLPS with UUID {}, {}", kOppfolgingsplanLPSNAV.getUuid(), callIdArgument(callId))
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED.increment()
            }
        } else {
            log.info("OppfolgingsplanLPS does not have BehovForBistandFraNav=true and is skipped, {}", callIdArgument(callId))
            COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND.increment()
        }
    }

    private fun sendPersonoppgavehendelse(
        personOppgaveUUID: UUID,
        personIdentNumber: PersonIdentNumber,
    ) {
        personoppgavehendelseProducer.sendPersonoppgavehendelse(
            PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT,
            personIdentNumber,
            personOppgaveUUID,
        )
    }
}
