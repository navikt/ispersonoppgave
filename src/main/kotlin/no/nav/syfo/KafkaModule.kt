package no.nav.syfo

import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmotestatusendring.kafka.launchKafkaTaskDialogmotestatusendring
import no.nav.syfo.dialogmotesvar.kafka.launchKafkaTaskDialogmotesvar
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.launchKafkaTaskIdenthendelse
import no.nav.syfo.behandlerdialog.kafka.launchKafkaTaskMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.launchKafkaTaskUbesvartMelding
import no.nav.syfo.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.oppfolgingsplanlps.kafka.launchKafkaTaskOppfolgingsplanLPS
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
        database = database,
        applicationState = applicationState,
        environment = environment,
    )

    launchKafkaTaskUbesvartMelding(
        database = database,
        applicationState = applicationState,
        environment = environment,
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
}
