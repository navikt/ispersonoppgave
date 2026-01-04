package no.nav.syfo.application

import no.nav.syfo.COUNT_PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT
import no.nav.syfo.COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED
import no.nav.syfo.COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED
import no.nav.syfo.COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.database.queries.PPersonOppgave
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.kafka.oppfolgingsplanlps.KOppfolgingsplanLPS
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.domain.PersonoppgavehendelseType
import no.nav.syfo.infrastructure.database.queries.createPersonOppgave
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaver
import no.nav.syfo.infrastructure.database.queries.updatePersonOppgaveOversikthendelse
import no.nav.syfo.util.callIdArgument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class OppfolgingsplanLPSService(
    private val database: DatabaseInterface,
    private val personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {
    fun receiveOppfolgingsplanLPS(
        kOppfolgingsplanLPS: KOppfolgingsplanLPS,
        callId: String = "",
    ) {
        if (kOppfolgingsplanLPS.behovForBistandFraNav) {
            val person: PPersonOppgave? = database.getPersonOppgaver(PersonIdent(kOppfolgingsplanLPS.fodselsnummer))
                .find { it.referanseUuid == UUID.fromString(kOppfolgingsplanLPS.uuid) }
            if (person == null) {
                log.info("Didn't find person with oppgave based on given referanseUuid: ${kOppfolgingsplanLPS.uuid} creating new Personoppgave")
                val uuid = database.connection.use { connection ->
                    connection.createPersonOppgave(
                        kOppfolgingsplanLPS,
                        PersonOppgaveType.OPPFOLGINGSPLANLPS,
                    ).also {
                        connection.commit()
                    }
                }
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED.increment()

                val fodselsnummer = PersonIdent(kOppfolgingsplanLPS.fodselsnummer)
                sendPersonoppgavehendelse(uuid, fodselsnummer)
                database.updatePersonOppgaveOversikthendelse(uuid)
                COUNT_PERSONOPPGAVEHENDELSE_OPPFOLGINGSPLANLPS_BISTAND_MOTTATT_SENT.increment()
            } else {
                log.error(
                    "Already create a PersonOppgave for OppfolgingsplanLPS with UUID {}, {}",
                    kOppfolgingsplanLPS.uuid,
                    callIdArgument(callId)
                )
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED.increment()
            }
        } else {
            log.info("OppfolgingsplanLPS does not have BehovForBistandFraNav=true and is skipped, {}", callIdArgument(callId))
            COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND.increment()
        }
    }

    private fun sendPersonoppgavehendelse(
        personOppgaveUUID: UUID,
        personIdent: PersonIdent,
    ) {
        personoppgavehendelseProducer.sendPersonoppgavehendelse(
            PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT,
            personIdent,
            personOppgaveUUID,
        )
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OppfolgingsplanLPSService::class.java)
    }
}
