package no.nav.syfo.testutil

import io.ktor.server.application.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
    personoppgavehendelseProducer: PersonoppgavehendelseProducer,
) {

    apiModule(
        applicationState = externalMockEnvironment.applicationState,
        database = externalMockEnvironment.database,
        environment = externalMockEnvironment.environment,
        personoppgavehendelseProducer = personoppgavehendelseProducer,
        wellKnownInternADV2 = externalMockEnvironment.wellKnownInternADV2Mock,
    )
}
