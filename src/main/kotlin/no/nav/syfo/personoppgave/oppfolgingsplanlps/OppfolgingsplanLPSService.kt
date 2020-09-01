package no.nav.syfo.personoppgave.oppfolgingsplanlps

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.metric.*
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.util.callIdArgument
import org.slf4j.LoggerFactory
import java.util.*

val log = LoggerFactory.getLogger("no.nav.syfo.personoppgave.oppfolgingsplanlps")

class OppfolgingsplanLPSService(
    private val database: DatabaseInterface
) {
    fun receiveOppfolgingsplanLPS(
        kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV,
        callId: String = ""
    ) {
        if (kOppfolgingsplanLPSNAV.getBehovForBistandFraNav() == true) {
            val person: PPersonOppgave? = database.getPersonOppgaveList(Fodselsnummer(kOppfolgingsplanLPSNAV.getFodselsnummer()))
                .find { it.uuid == UUID.fromString(kOppfolgingsplanLPSNAV.getUuid()) }
            if (person == null) {
                database.createPersonOppgave(
                    kOppfolgingsplanLPSNAV,
                    PersonOppgaveType.OPPFOLGINGSPLANLPS
                )
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_CREATED.inc()
            } else {
                log.error("Already create a PersonOppgave for OppfolgingsplanLPS with UUID {}, {}", kOppfolgingsplanLPSNAV.getUuid(), callIdArgument(callId))
                COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_ALREADY_CREATED.inc()
            }
        } else {
            log.info("OppfolgingsplanLPS does not have BehovForBistandFraNav=true and is skipped, {}", callIdArgument(callId))
            COUNT_PERSON_OPPGAVE_OPPFOLGINGSPLANLPS_NO_BEHOVFORBISTAND.inc()
        }
    }
}
