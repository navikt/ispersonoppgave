package no.nav.syfo.personoppgavehendelse.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.cronjob.Cronjob
import no.nav.syfo.cronjob.CronjobResult
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import org.slf4j.LoggerFactory

class PublishOppgavehendelseCronjob(
    private val publishOppgavehendelseService: PublishPersonoppgavehendelseService,
) : Cronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 1 // TODO: test which value this should be. It probably should be less than a minute

    override suspend fun run() {
        publishOppgavehendelserJob()
    }

    fun publishOppgavehendelserJob(): CronjobResult {
        val result = CronjobResult()

        val unpublishedOppgaver = publishOppgavehendelseService.getUnpublishedOppgaver()
        unpublishedOppgaver.forEach { personOppgave ->
            try {
                publishOppgavehendelseService.publish(personOppgave)
                result.updated++
            } catch (e: Exception) {
                log.error("Exception caught while attempting to publish Oppgavehendelser", e)
                result.failed++
            }
        }
        if (result.failed + result.updated > 0) {
            log.info(
                "Completed oppgavehendelser-publishing with result: {}, {}",
                StructuredArguments.keyValue("failed", result.failed),
                StructuredArguments.keyValue("updated", result.updated),
            )
        }
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(PublishOppgavehendelseCronjob::class.java)
    }
}
