package no.nav.syfo.behandlerdialog

import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaverByReferanseUuid
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKMeldingDTO
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class UbesvartMeldingSpek : Spek({
    describe("Handle ubesvart-melding topic") {
        val externalMockEnvironment = ExternalMockEnvironment()
        val database = externalMockEnvironment.database
        val kafkaConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
        val personoppgavehendelseProducer = mockk<PersonoppgavehendelseProducer>()
        val personOppgaveService = PersonOppgaveService(
            database = database,
            personoppgavehendelseProducer = personoppgavehendelseProducer,
            personoppgaveRepository = PersonOppgaveRepository(database = database)
        )
        val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
        val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)

        beforeEachTest {
            every { kafkaConsumer.commitSync() } returns Unit
        }

        afterEachTest {
            database.dropData()
            clearMocks(personoppgavehendelseProducer)
        }

        it("stores ubesvart melding from kafka as oppgave in database and publish as new oppgave") {
            val referanseUuid = UUID.randomUUID()
            val kMeldingDTO = generateKMeldingDTO(referanseUuid)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = kMeldingDTO,
            )
            justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }

            kafkaUbesvartMelding.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer,
            )

            val personOppgave = database.connection.use { connection ->
                connection.getPersonOppgaverByReferanseUuid(
                    referanseUuid = referanseUuid,
                ).map { it.toPersonOppgave() }.first()
            }
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
})
