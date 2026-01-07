package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.kafka.sykmelding.launchKafkaTaskSykmelding
import no.nav.syfo.application.AvvistMeldingService
import no.nav.syfo.application.MeldingFraBehandlerService
import no.nav.syfo.application.UbesvartMeldingService
import no.nav.syfo.infrastructure.kafka.behandlerdialog.launchKafkaTaskAvvistMelding
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.kafka.dialogmotestatusendring.launchKafkaTaskDialogmotestatusendring
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseConsumer
import no.nav.syfo.infrastructure.kafka.identhendelse.launchKafkaTaskIdenthendelse
import no.nav.syfo.infrastructure.kafka.behandlerdialog.launchKafkaTaskMeldingFraBehandler
import no.nav.syfo.infrastructure.kafka.behandlerdialog.launchKafkaTaskUbesvartMelding
import no.nav.syfo.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.application.OppfolgingsplanLPSService
import no.nav.syfo.infrastructure.kafka.oppfolgingsplanlps.launchKafkaTaskOppfolgingsplanLPS
import no.nav.syfo.application.PersonOppgaveService
import no.nav.syfo.infrastructure.kafka.dialogmotesvar.launchKafkaTaskDialogmotesvar
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer

fun launchKafkaTasks(
    applicationState: ApplicationState,
    database: DatabaseInterface,
    environment: Environment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
    pdlClient: PdlClient,
) {
    val oppfolgingsplanLPSService = OppfolgingsplanLPSService(
        database,
        personoppgavehendelseProducer,
    )

    val personOppgaveRepository = PersonOppgaveRepository(database = database)
    val personOppgaveService = PersonOppgaveService(
        database = database,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        personoppgaveRepository = personOppgaveRepository,
    )

    val meldingFraBehandlerService = MeldingFraBehandlerService(
        database = database,
        personOppgaveService = personOppgaveService,
    )

    val ubesvartMeldingService = UbesvartMeldingService(
        personOppgaveService = personOppgaveService,
    )

    val avvistMeldingService = AvvistMeldingService(
        database = database,
        personOppgaveService = personOppgaveService,
    )

    launchKafkaTaskOppfolgingsplanLPS(
        applicationState = applicationState,
        environment = environment,
        oppfolgingsplanLPSService = oppfolgingsplanLPSService,
    )
    launchKafkaTaskDialogmotestatusendring(
        database = database,
        applicationState = applicationState,
        environment = environment,
    )

    launchKafkaTaskDialogmotesvar(
        database = database,
        applicationState = applicationState,
        environment = environment,
    )

    launchKafkaTaskMeldingFraBehandler(
        applicationState = applicationState,
        environment = environment,
        meldingFraBehandlerService = meldingFraBehandlerService,
    )

    launchKafkaTaskUbesvartMelding(
        database = database,
        applicationState = applicationState,
        environment = environment,
        ubesvartMeldingService = ubesvartMeldingService,
    )

    launchKafkaTaskAvvistMelding(
        applicationState = applicationState,
        environment = environment,
        avvistMeldingService = avvistMeldingService,
    )

    val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )
    val kafkaIdenthendelseConsumer = IdenthendelseConsumer(
        identhendelseService = identhendelseService,
    )
    launchKafkaTaskIdenthendelse(
        applicationState = applicationState,
        environment = environment,
        kafkaIdenthendelseConsumer = kafkaIdenthendelseConsumer,
    )

    launchKafkaTaskSykmelding(
        applicationState = applicationState,
        environment = environment,
        database = database,
        personOppgaveRepository = personOppgaveRepository,
    )
}
