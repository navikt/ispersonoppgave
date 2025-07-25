package no.nav.syfo

import no.nav.syfo.sykmelding.launchKafkaTaskSykmelding
import no.nav.syfo.behandlerdialog.AvvistMeldingService
import no.nav.syfo.behandlerdialog.MeldingFraBehandlerService
import no.nav.syfo.behandlerdialog.UbesvartMeldingService
import no.nav.syfo.behandlerdialog.kafka.launchKafkaTaskAvvistMelding
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
import no.nav.syfo.dialogmotestatusendring.kafka.launchKafkaTaskDialogmotestatusendring
import no.nav.syfo.dialogmotesvar.kafka.launchKafkaTaskDialogmotesvar
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.behandlerdialog.kafka.launchKafkaTaskMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.launchKafkaTaskUbesvartMelding
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.oppfolgingsplanlps.kafka.launchKafkaTaskOppfolgingsplanLPS
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

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
    val kafkaIdenthendelseConsumerService = IdenthendelseConsumerService(
        identhendelseService = identhendelseService,
    )
    launchKafkaTaskIdenthendelse(
        applicationState = applicationState,
        environment = environment,
        kafkaIdenthendelseConsumerService = kafkaIdenthendelseConsumerService,
    )

    launchKafkaTaskSykmelding(
        applicationState = applicationState,
        environment = environment,
        database = database,
        personOppgaveRepository = personOppgaveRepository,
    )
}
