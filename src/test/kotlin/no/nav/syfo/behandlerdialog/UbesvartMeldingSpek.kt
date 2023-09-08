package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockReceiveMeldingDTO
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class UbesvartMeldingSpek : Spek({
    describe("Handle ubesvart-melding topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val personoppgavehendelseProducer = mockk<PersonoppgavehendelseProducer>()
            val personOppgaveService = PersonOppgaveService(
                database = database,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )
            val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
            val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.connection.dropData()
                clearMocks(personoppgavehendelseProducer)
            }

            it("stores ubesvart melding from kafka as oppgave in database and publish as new oppgave") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingDTO = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }

                kafkaUbesvartMelding.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val personOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                ).map { it.toPersonOppgave() }.first()
                personOppgave.publish shouldBeEqualTo false
                personOppgave.type.name shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART.name

                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT,
                        personIdent = personOppgave.personIdent,
                        personoppgaveId = personOppgave.uuid,
                    )
                }
            }
        }
    }
})
