package no.nav.syfo

import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmotestatusendring.kafka.consumeDialogmotestatusendring
import no.nav.syfo.dialogmotesvar.kafka.consumeDialogmotesvar
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService
import no.nav.syfo.identhendelse.kafka.consumeIdenthendelse
import no.nav.syfo.personoppgave.oppfolgingsplanlps.OppfolgingsplanLPSService
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.blockingApplicationLogicOppfolgingsplanLPS
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

    launchBackgroundTask(applicationState) {
        log.info("Launch launchBackgroundTask for oppfolginsplanLPS")
        blockingApplicationLogicOppfolgingsplanLPS(
            applicationState = applicationState,
            environment = environment,
            oppfolgingsplanLPSService = oppfolgingsplanLPSService,
        )
    }
    if (environment.toggleKafkaConsumerStatusendringEnabled) {
        launchBackgroundTask(applicationState) {
            log.info("Launch launchBackgroundTask for Dialogmøtestatusendringer")
            consumeDialogmotestatusendring(
                database = database,
                applicationState = applicationState,
                environment = environment,
            )
        }
    }

    if (environment.toggleKafkaConsumerDialogmotesvarEnabled) {
        launchBackgroundTask(applicationState) {
            log.info("Launch background task for dialogmotesvar")
            consumeDialogmotesvar(
                database = database,
                applicationState = applicationState,
                environment = environment,
            )
        }
    }

    launchBackgroundTask(applicationState) {
        log.info("Launch background task for Identhendelse from PDL-aktor")
        val identhendelseService = IdenthendelseService(
            database = database,
            pdlClient = pdlClient,
        )
        val kafkaIdenthendelseConsumerService = IdenthendelseConsumerService(
            identhendelseService = identhendelseService,
        )
        consumeIdenthendelse(
            applicationState = applicationState,
            environment = environment,
            kafkaIdenthendelseConsumerService = kafkaIdenthendelseConsumerService,
        )
    }
}
