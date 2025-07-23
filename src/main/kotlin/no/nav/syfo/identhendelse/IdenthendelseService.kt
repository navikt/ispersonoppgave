package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.identhendelse.database.*
import no.nav.syfo.identhendelse.kafka.COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES
import no.nav.syfo.identhendelse.kafka.KafkaIdenthendelseDTO
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
                val numberOfUpdatedRows = updateAllTables(activeIdent = activeIdent, inactiveIdenter = inactiveIdenter)
                if (numberOfUpdatedRows > 0) {
                    log.info("Identhendelse: Updated $numberOfUpdatedRows rows based on Identhendelse from PDL")
                    COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES.increment(numberOfUpdatedRows.toDouble())
                }
            }
        }
    }

    private fun updateAllTables(activeIdent: PersonIdent, inactiveIdenter: List<PersonIdent>): Int {
        var numberOfUpdatedIdenter = 0
        val inactiveIdenterCount = database.getIdentCount(inactiveIdenter)

        if (inactiveIdenterCount > 0) {
            checkThatPdlIsUpdated(activeIdent)
            database.connection.use { connection ->
                numberOfUpdatedIdenter += connection.updateMotesvar(activeIdent, inactiveIdenter)
                numberOfUpdatedIdenter += connection.updateDialogmoteStatusendring(activeIdent, inactiveIdenter)
                numberOfUpdatedIdenter = connection.updatePersonOppgave(activeIdent, inactiveIdenter)
                connection.commit()
            }
        }

        return numberOfUpdatedIdenter
    }

    // Erfaringer fra andre team tilsier at vi burde dobbeltsjekke at ting har blitt oppdatert i PDL før vi gjør endringer
    private fun checkThatPdlIsUpdated(nyIdent: PersonIdent) {
        runBlocking {
            val pdlIdenter = pdlClient.getPdlIdenter(nyIdent)?.hentIdenter ?: throw RuntimeException("Fant ingen identer fra PDL")
            if (nyIdent.value != pdlIdenter.aktivIdent && pdlIdenter.identhendelseIsNotHistorisk(nyIdent.value)) {
                throw IllegalStateException("Ny ident er ikke aktiv ident i PDL")
            }
        }
    }
}
