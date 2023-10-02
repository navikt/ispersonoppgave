package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_AKTIVITETSKRAV_EXPIRED_VARSEL_MOTTATT
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VurderStoppService(
    private val database: DatabaseInterface,
    private val personOppgaveService: PersonOppgaveService,
) {
    fun processAktivitetskravExpiredVarsel(recordPairs: List<Pair<String, ExpiredVarsel>>) {
        database.connection.use { connection ->
            recordPairs.forEach { record ->
                val expiredVarsel = record.second
                log.info("Received aktivitetskrav expired varsel with key=${record.first} and uuid=${expiredVarsel.uuid}")
                val personIdent = PersonIdent(expiredVarsel.personIdent)
                val oppgaveUuid = connection.createPersonOppgave(
                    referanseUuid = expiredVarsel.uuid,
                    personIdent = personIdent,
                    personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                )

                personOppgaveService.publishPersonoppgaveHendelse(
                    personoppgavehendelseType = PersonoppgavehendelseType.AKTIVITETSKRAV_VURDER_STANS_MOTTATT,
                    personIdent = personIdent,
                    personoppgaveUUID = oppgaveUuid,
                )

                COUNT_PERSONOPPGAVEHENDELSE_AKTIVITETSKRAV_EXPIRED_VARSEL_MOTTATT.increment()
            }
            connection.commit()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
