package no.nav.syfo.arbeidsuforhet.kafka

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.arbeidsuforhet.VurderAvslagService
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateArbeidsuforhetVurdering
import no.nav.syfo.testutil.generators.generatePersonoppgave
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import no.nav.syfo.util.toOffsetDateTimeUTC
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

private val vurderingOppfylt = generateArbeidsuforhetVurdering(type = VurderingType.OPPFYLT)
private val vurderingForhandsvarsel = generateArbeidsuforhetVurdering(type = VurderingType.FORHANDSVARSEL)
private val vurderingAvslag = generateArbeidsuforhetVurdering(type = VurderingType.AVSLAG)

val oppgaveOpprettet: LocalDateTime = LocalDateTime.now().minusDays(1)
private val ubehandletVurderAvslagOppgave = generatePersonoppgave(
    type = PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG,
    opprettet = oppgaveOpprettet
)

class ArbeidsuforhetVurderingConsumerSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment()
    val database = externalMockEnvironment.database
    val kafkaConsumer = mockk<KafkaConsumer<String, ArbeidsuforhetVurdering>>()
    val personOppgaveRepository = PersonOppgaveRepository(database = database)
    val vurderAvslagService = VurderAvslagService(
        database = database,
        personOppgaveRepository = personOppgaveRepository
    )

    val arbeidsuforhetVurderingConsumer = ArbeidsuforhetVurderingConsumer(
        kafkaEnvironment = externalMockEnvironment.environment.kafka,
        applicationState = externalMockEnvironment.applicationState,
        vurderAvslagService = vurderAvslagService,
    )

    beforeEachTest {
        every { kafkaConsumer.commitSync() } returns Unit
    }

    afterEachTest {
        database.dropData()
        clearMocks(kafkaConsumer)
    }

    describe("pollAndProcessRecords") {
        it("Behandler existing ubehandlet vurder avslag-oppgave when vurdering is OPPFYLT") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOppfylt,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldBeEqualTo vurderingOppfylt.personident
            personOppgave.publish.shouldBeTrue()
            personOppgave.behandletTidspunkt.shouldNotBeNull()
            personOppgave.behandletVeilederIdent shouldBeEqualTo vurderingOppfylt.veilederident
        }
        it("Behandler existing ubehandlet vurder avslag-oppgave when vurdering is AVSLAG") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingAvslag,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldBeEqualTo vurderingAvslag.personident
            personOppgave.publish.shouldBeTrue()
            personOppgave.behandletTidspunkt.shouldNotBeNull()
            personOppgave.behandletVeilederIdent shouldBeEqualTo vurderingOppfylt.veilederident
        }
        it("Will not behandle when vurdering is FORHANDSVARSEL") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingForhandsvarsel,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldBeEqualTo vurderingForhandsvarsel.personident
            personOppgave.publish.shouldBeFalse()
            personOppgave.behandletTidspunkt.shouldBeNull()
            personOppgave.behandletVeilederIdent.shouldBeNull()
        }

        it("Will not behandle when no existing vurder avslag-oppgave") {
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOppfylt,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val pPersonOppgaver = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            )
            pPersonOppgaver.size shouldBeEqualTo 0
        }

        it("Will not behandle when vurder avslag-oppgave already behandlet") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            val behandletOppgave = ubehandletVurderAvslagOppgave.behandle(veilederIdent = UserConstants.VEILEDER_IDENT)
            personOppgaveRepository.updatePersonoppgaveBehandlet(personOppgave = behandletOppgave)

            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOppfylt,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgaver = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver()
            personOppgaver.size shouldBeEqualTo 1
            val personOppgave = personOppgaver.first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldBeEqualTo vurderingOppfylt.personident
            personOppgave.behandletTidspunkt?.truncatedTo(ChronoUnit.MILLIS)!! shouldNotBeGreaterThan behandletOppgave.behandletTidspunkt?.truncatedTo(ChronoUnit.MILLIS)!!
            personOppgave.behandletVeilederIdent shouldBeEqualTo behandletOppgave.behandletVeilederIdent
        }

        it("Will not behandle when vurdering is older than existing oppgave") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingAvslag.copy(
                    createdAt = oppgaveOpprettet.minusDays(1).toOffsetDateTimeUTC()
                ),
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldBeEqualTo vurderingAvslag.personident
            personOppgave.publish.shouldBeFalse()
            personOppgave.behandletTidspunkt.shouldBeNull()
            personOppgave.behandletVeilederIdent.shouldBeNull()
        }

        it("Will not behandle when vurdering personident differs from ubehandlet oppgave personident") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            val vurderingOtherPerson = vurderingOppfylt.copy(personident = UserConstants.ARBEIDSTAKER_3_FNR.value)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOtherPerson,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
            personOppgave.personIdent.value shouldNotBeEqualTo vurderingOtherPerson.personident
            personOppgave.publish.shouldBeFalse()
            personOppgave.behandletTidspunkt.shouldBeNull()
            personOppgave.behandletVeilederIdent.shouldBeNull()
        }

        it("Will not behandle if existing oppgave is not vurder avslag") {
            val ubehandletVurderStansOppgave = generatePersonoppgave(
                type = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                opprettet = oppgaveOpprettet
            )

            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderStansOppgave)
            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOppfylt,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            verify(exactly = 1) {
                kafkaConsumer.commitSync()
            }

            val personOppgave = database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().first()
            personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
            personOppgave.publish.shouldBeFalse()
            personOppgave.behandletTidspunkt.shouldBeNull()
            personOppgave.behandletVeilederIdent.shouldBeNull()
        }

        it("Behandler all ubehandlet vurder avslag-oppgaver for person") {
            personOppgaveRepository.createPersonoppgave(personOppgave = ubehandletVurderAvslagOppgave)
            personOppgaveRepository.createPersonoppgave(
                personOppgave = ubehandletVurderAvslagOppgave.copy(
                    uuid = UUID.randomUUID(),
                    referanseUuid = UUID.randomUUID(),
                )
            )

            kafkaConsumer.mockPollConsumerRecords(
                recordValue = vurderingOppfylt,
                topic = ArbeidsuforhetVurderingConsumer.ARBEIDSUFORHET_VURDERING_TOPIC,
            )

            arbeidsuforhetVurderingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

            database.getPersonOppgaver(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            ).toPersonOppgaver().forEach {
                it.type shouldBeEqualTo PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
                it.personIdent.value shouldBeEqualTo vurderingOppfylt.personident
                it.publish.shouldBeTrue()
                it.behandletTidspunkt.shouldNotBeNull()
                it.behandletVeilederIdent shouldBeEqualTo vurderingOppfylt.veilederident
            }
        }
    }
})
