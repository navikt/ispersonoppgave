package no.nav.syfo.arbeidsuforhet.kafka

import io.mockk.*
import no.nav.syfo.arbeidsuforhet.VurderAvslagService
import no.nav.syfo.arbeidsuforhet.kafka.ExpiredForhandsvarselConsumer.Companion.ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.updatePersonOppgaveBehandlet
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateExpiredForhandsvarsel
import no.nav.syfo.testutil.getAllPersonoppgaver
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ExpiredForhandsvarselConsumerSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment()
    val database = externalMockEnvironment.database
    val kafkaConsumer = mockk<KafkaConsumer<String, ExpiredForhandsvarsel>>()
    val personOppgaveRepository = PersonOppgaveRepository(database = database)
    val vurderAvslagService = VurderAvslagService(
        database = database,
        personOppgaveRepository = personOppgaveRepository
    )

    val expiredForhandsvarselConsumer = ExpiredForhandsvarselConsumer(
        kafkaEnvironment = externalMockEnvironment.environment.kafka,
        applicationState = externalMockEnvironment.applicationState,
        vurderAvslagService = vurderAvslagService,
    )
    val expiredForhandsvarsel = generateExpiredForhandsvarsel()
    beforeEachTest {
        every { kafkaConsumer.commitSync() } returns Unit
    }

    afterEachTest {
        database.dropData()
        clearMocks(kafkaConsumer)
    }

    describe("pollAndProcessRecords") {
        it("consumes expired forhandsvarsel and creates oppgave") {
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = expiredForhandsvarsel,
                topic = ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC,
            )

            expiredForhandsvarselConsumer.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer
            )

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personoppgave = database.getAllPersonoppgaver().single().toPersonOppgave()

            personoppgave.referanseUuid shouldBeEqualTo expiredForhandsvarsel.uuid
            personoppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personoppgave.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR
            personoppgave.publish shouldBeEqualTo true
            personoppgave.isUBehandlet() shouldBeEqualTo true
        }

        it("consumes duplicate expired forhandsvarsel and creates oppgave once") {
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = expiredForhandsvarsel,
                recordValue2 = expiredForhandsvarsel.copy(),
                topic = ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC,
            )

            expiredForhandsvarselConsumer.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer
            )

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personoppgave = database.getAllPersonoppgaver().single().toPersonOppgave()

            personoppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
        }

        it("consumes expired forhandsvarsel and creates oppgave if existing is behandlet") {
            val existingPersonoppgave = expiredForhandsvarsel.toPersonoppgave()
            personOppgaveRepository.createPersonoppgave(personOppgave = existingPersonoppgave)
            val existingPersonoppgaveBehandlet =
                existingPersonoppgave.behandle(veilederIdent = UserConstants.VEILEDER_IDENT)
            database.updatePersonOppgaveBehandlet(updatedPersonoppgave = existingPersonoppgaveBehandlet)

            kafkaConsumer.mockPollConsumerRecords(
                recordValue = expiredForhandsvarsel,
                recordValue2 = expiredForhandsvarsel.copy(),
                topic = ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC,
            )

            expiredForhandsvarselConsumer.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer
            )

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personoppgaver = database.getAllPersonoppgaver().toPersonOppgaver()
            personoppgaver.size shouldBeEqualTo 2
            val (behandletOppgaver, ubehandletOppgaver) = personoppgaver.partition { it.isBehandlet() }

            val behandletOppgave = behandletOppgaver.first()
            behandletOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            behandletOppgave.referanseUuid shouldBeEqualTo expiredForhandsvarsel.uuid
            behandletOppgave.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR
            behandletOppgave.uuid shouldBeEqualTo existingPersonoppgave.uuid

            val ubehandletOppgave = ubehandletOppgaver.first()
            ubehandletOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            ubehandletOppgave.referanseUuid shouldBeEqualTo expiredForhandsvarsel.uuid
            ubehandletOppgave.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR
            ubehandletOppgave.publish shouldBeEqualTo true
            ubehandletOppgave.isUBehandlet() shouldBeEqualTo true
            ubehandletOppgave.uuid shouldNotBeEqualTo existingPersonoppgave.uuid
        }
    }
})
