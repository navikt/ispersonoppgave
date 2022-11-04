package no.nav.syfo.cronjob.oppgavehendelse

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cronjob.Cronjob
import no.nav.syfo.cronjob.CronjobResult
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import org.slf4j.LoggerFactory

class PublishOppgavehendelseCronjob(
    private val database: DatabaseInterface,
    private val publishOppgavehendelseService: PublishPersonoppgavehendelseService,
) : Cronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 1 // TODO: test which value this should be. It probably should be less than a minute

    override suspend fun run() {
        publishOppgavehendelserJob()
    }

    fun publishOppgavehendelserJob(): CronjobResult {
        val result = CronjobResult()

        val unpublishedOppgaver: List<PersonOppgave>
        database.connection.use { connection ->
            unpublishedOppgaver = publishOppgavehendelseService.getUnpublishedOppgaver(connection)
        }
        unpublishedOppgaver.forEach { personOppgave ->
            try {
                database.connection.use { connection ->
                    publishOppgavehendelseService.publish(connection, personOppgave)
                    connection.commit()
                }
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting to publish Oppgavehendelser", e)
                result.failed++
            }
        }
        // TODO: Kun logg hvis noe har blitt prosessert
        log.info(
            "Completed oppgavehendelser-publishing with result: {}, {}",
            StructuredArguments.keyValue("failed", result.failed),
            StructuredArguments.keyValue("updated", result.updated),
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishOppgavehendelseCronjob::class.java)
    }
}
