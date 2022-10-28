package no.nav.syfo.cronjob

import kotlinx.coroutines.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.ApplicationState
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import org.slf4j.LoggerFactory
import java.time.Duration

class CronjobRunner(
    private val applicationState: ApplicationState,
    private val leaderPodClient: LeaderPodClient,
) {
    private val log = LoggerFactory.getLogger(CronjobRunner::class.java)

    suspend fun start(cronjob: Cronjob) = coroutineScope {
        val (initialDelay, intervalDelay) = delays(cronjob)
        log.info(
            "Scheduling start of ${cronjob.javaClass.simpleName}: {} ms, {} ms",
            StructuredArguments.keyValue("initialDelay", initialDelay),
            StructuredArguments.keyValue("intervalDelay", intervalDelay),
        )
        delay(initialDelay)

        while (applicationState.ready) {
            val job = launch() { run(cronjob) }
            delay(intervalDelay)
            if (job.isActive) {
                log.info("Waiting for job to finish")
                job.join()
            }
        }
        log.info("Ending ${cronjob.javaClass.simpleName} due to failed liveness check ")
    }

    private suspend fun run(cronjob: Cronjob) {
        try {
            if (leaderPodClient.isLeader()) {
                cronjob.run()
            } else {
                log.debug("Pod is not leader and will not perform job")
            }
        } catch (ex: Exception) {
            log.error("Exception in ${cronjob.javaClass.simpleName}. Job will run again after delay.", ex)
        }
    }

    private fun delays(cronjob: Cronjob): Pair<Long, Long> {
        val initialDelay = Duration.ofMinutes(cronjob.initialDelayMinutes).toMillis()
        val intervalDelay = Duration.ofMinutes(cronjob.intervalDelayMinutes).toMillis()
        return Pair(initialDelay, intervalDelay)
    }
}
