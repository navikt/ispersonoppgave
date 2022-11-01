package no.nav.syfo.cronjob

import io.ktor.server.application.*
import no.nav.syfo.*
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.cronjob.oppgavehendelse.PublishOppgavehendelseCronjob
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {

    val leaderPodClient = LeaderPodClient(
        environment = environment,
    )

    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient
    )

    val publishOppgavehendelseService = PublishPersonoppgavehendelseService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
    )

    val publishOppgavehendelseCronjob = PublishOppgavehendelseCronjob(
        publishOppgavehendelseService = publishOppgavehendelseService,
    )

    if (environment.publishOppgavehendelser) {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = publishOppgavehendelseCronjob)
        }
    }
}
