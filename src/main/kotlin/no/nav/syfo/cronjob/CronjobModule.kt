package no.nav.syfo.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.cronjob.leaderelection.LeaderPodClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.launchBackgroundTask
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import no.nav.syfo.personoppgavehendelse.cronjob.PublishOppgavehendelseCronjob

fun cronjobModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
    personOppgaveRepository: PersonOppgaveRepository,
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
        personOppgaveRepository = personOppgaveRepository,
    )

    val publishOppgavehendelseCronjob = PublishOppgavehendelseCronjob(
        publishOppgavehendelseService = publishOppgavehendelseService,
    )

    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        cronjobRunner.start(cronjob = publishOppgavehendelseCronjob)
    }
}
