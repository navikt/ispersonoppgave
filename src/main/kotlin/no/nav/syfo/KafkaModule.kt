package no.nav.syfo

import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.dialogmotestatusendring.kafka.consumeDialogmotestatusendring
import no.nav.syfo.dialogmotesvar.kafka.consumeDialogmotesvar
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

    if (environment.toggleKafkaConsumerIdenthendelseEnabled) {
        launchBackgroundTask(applicationState) {
            log.info("Launch background task for Identhendelse from PDL-aktor")
            val kafkaIdenthendelseConsumerService = IdenthendelseConsumerService()
            consumeIdenthendelse(
                applicationState = applicationState,
                environment = environment,
                kafkaIdenthendelseConsumerService = kafkaIdenthendelseConsumerService,
            )
        }
    }
}
