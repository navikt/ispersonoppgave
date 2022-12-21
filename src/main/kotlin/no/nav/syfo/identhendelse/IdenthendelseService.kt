package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.identhendelse.database.updatePersonOppgave
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
import no.nav.syfo.personoppgave.getPersonOppgaveList
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(
    private val database: DatabaseInterface,
    private val pdlClient: PdlClient,
) {

    private val log: Logger = LoggerFactory.getLogger(IdenthendelseService::class.java)

    fun handleIdenthendelse(identhendelse: KafkaIdenthendelseDTO) {
        if (identhendelse.folkeregisterIdenter.size > 1) {
            val activeIdent = identhendelse.getActivePersonident()
            if (activeIdent != null) {
                val inactiveIdenter = identhendelse.getInactivePersonidenter()
                val personOppgaveWithOldIdentList = inactiveIdenter.flatMap { personident ->
                    database.getPersonOppgaveList(personident)
                }

                if (personOppgaveWithOldIdentList.isNotEmpty()) {
                    checkThatPdlIsUpdated(activeIdent)
                    val numberOfUpdatedIdenter = database.updatePersonOppgave(activeIdent, personOppgaveWithOldIdentList)
                    log.info("Identhendelse: Updated $numberOfUpdatedIdenter personoppgaver based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedIdenter.toDouble())
                }
            } else {
                log.warn("Mangler gyldig ident fra PDL")
            }
        }
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdent) {
        runBlocking {
            val pdlIdenter = pdlClient.getPdlIdenter(nyIdent)?.hentIdenter
            if (nyIdent.value != pdlIdenter?.aktivIdent || pdlIdenter.identer.any { it.ident == nyIdent.value && it.historisk }) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
