package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.launchBackgroundTask
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.application.PublishPersonoppgavehendelseService

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
