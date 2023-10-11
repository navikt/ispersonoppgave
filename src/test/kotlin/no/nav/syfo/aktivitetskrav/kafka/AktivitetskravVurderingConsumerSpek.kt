package no.nav.syfo.aktivitetskrav.kafka

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.aktivitetskrav.VurderStansService
import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.behandle
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.updatePersonoppgaveSetBehandlet
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testutil.createPersonOppgave
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generatePersonoppgave
import no.nav.syfo.testutil.generators.generateKafkaAktivitetskravVurdering
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.*
import org.amshove.kluent.internal.assertFailsWith
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class AktivitetskravVurderingConsumerSpek : Spek({
    describe(AktivitetskravVurderingConsumer::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val topic = AktivitetskravVurderingConsumer.AKTIVITETSKRAV_VURDERING_TOPIC
            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KafkaAktivitetskravVurdering>>()
            val vurderStansService = VurderStansService(
                database = database,
            )
            val aktivitetskravVurderingConsumer = AktivitetskravVurderingConsumer(
                vurderStansService = vurderStansService,
            )

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.dropData()
                clearMocks(kafkaConsumer)
            }

            describe("Consume aktivitetskravvurderinger") {
                val vurderingUnntak = generateKafkaAktivitetskravVurdering()
                val ubehandletVurderStansOppgave = generatePersonoppgave(
                    personIdent = ARBEIDSTAKER_FNR,
                    type = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                    opprettet = LocalDateTime.now().minusHours(1),
                    sistEndret = LocalDateTime.now().minusHours(1),
                )

                fun createPersonoppgaver(vararg oppgaver: PersonOppgave) {
                    database.connection.use {
                        oppgaver.forEach { oppgave ->
                            it.createPersonOppgave(oppgave)
                        }
                        it.commit()
                    }
                }

                it("Will behandle existing ubehandlet vurder_stans oppgave when status is a final state (here: UNNTAK)") {
                    createPersonoppgaver(ubehandletVurderStansOppgave)
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingUnntak,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = PersonIdent(vurderingUnntak.personIdent),
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent.value shouldBeEqualTo vurderingUnntak.personIdent
                    personOppgave.publish shouldBeEqualTo true
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldNotBeEqualTo null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo vurderingUnntak.updatedBy
                }

                it("Will not behandle when no existing vurder-stans oppgave") {
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingUnntak,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val pPersonOppgaver = database.getPersonOppgaver(
                        personIdent = PersonIdent(vurderingUnntak.personIdent),
                    )
                    pPersonOppgaver.size shouldBeEqualTo 0
                }

                it("Will not behandle when vurder-stans oppgave already behandlet") {
                    val originalVeileder = VEILEDER_IDENT
                    val alreadyBehandletVurderStansOppgave = ubehandletVurderStansOppgave.behandle(originalVeileder)
                    database.connection.use { connection ->
                        connection.createPersonOppgave(alreadyBehandletVurderStansOppgave)
                        connection.updatePersonoppgaveSetBehandlet(alreadyBehandletVurderStansOppgave)
                        connection.commit()
                    }
                    val vurderingUnntakByOtherVeileder = vurderingUnntak.copy(updatedBy = "X000000")

                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingUnntakByOtherVeileder,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = alreadyBehandletVurderStansOppgave.personIdent,
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent shouldBeEqualTo alreadyBehandletVurderStansOppgave.personIdent
                    personOppgave.publish shouldBeEqualTo false
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldNotBe null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo alreadyBehandletVurderStansOppgave.behandletVeilederIdent
                }

                it("Will not behandle when status for vurdering is not a final state for aktivitetskrav") {
                    createPersonoppgaver(ubehandletVurderStansOppgave)
                    val vurderingAvvent = vurderingUnntak.copy(status = AktivitetskravStatus.AVVENT.name)
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingAvvent,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = ubehandletVurderStansOppgave.personIdent,
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent shouldBeEqualTo ubehandletVurderStansOppgave.personIdent
                    personOppgave.publish shouldBeEqualTo false
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldBeEqualTo null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo null
                }

                it("Will not behandle when vurdering has neither sistVurdert nor updatedBy (eg. has status NY)") {
                    createPersonoppgaver(ubehandletVurderStansOppgave)
                    val vurderingNyUtenSistVurdert = vurderingUnntak.copy(
                        status = AktivitetskravStatus.NY.name,
                        sistVurdert = null,
                        updatedBy = null,
                    )
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingNyUtenSistVurdert,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = ubehandletVurderStansOppgave.personIdent,
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent shouldBeEqualTo ubehandletVurderStansOppgave.personIdent
                    personOppgave.publish shouldBeEqualTo false
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldBeEqualTo null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo null
                }

                it("Will not behandle when vurdering is older than existing oppgave") {
                    createPersonoppgaver(ubehandletVurderStansOppgave)
                    val vurderingOlderThanOppgave = vurderingUnntak.copy(
                        sistVurdert = OffsetDateTime.now().minusDays(1),
                    )
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingOlderThanOppgave,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = ubehandletVurderStansOppgave.personIdent,
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent shouldBeEqualTo ubehandletVurderStansOppgave.personIdent
                    personOppgave.publish shouldBeEqualTo false
                    personOppgave.type shouldBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldBeEqualTo null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo null
                }

                it("Will not behandle if existing oppgave is not vurder-stans") {
                    val otherUbehandletPersonoppgave = ubehandletVurderStansOppgave.copy(type = PersonOppgaveType.BEHANDLERDIALOG_SVAR)
                    createPersonoppgaver(otherUbehandletPersonoppgave)
                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingUnntak,
                        topic = topic,
                    )

                    aktivitetskravVurderingConsumer.pollAndProcessRecords(
                        kafkaConsumer = kafkaConsumer,
                    )
                    verify(exactly = 1) {
                        kafkaConsumer.commitSync()
                    }

                    val personOppgave = database.getPersonOppgaver(
                        personIdent = otherUbehandletPersonoppgave.personIdent,
                    ).map { it.toPersonOppgave() }.first()
                    personOppgave.personIdent shouldBeEqualTo otherUbehandletPersonoppgave.personIdent
                    personOppgave.publish shouldBeEqualTo false
                    personOppgave.type shouldNotBeEqualTo PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS
                    personOppgave.behandletTidspunkt shouldBeEqualTo null
                    personOppgave.behandletVeilederIdent shouldBeEqualTo null
                }

                it("Will throw error in consumer when more than one ubehandlet vurder-stans oppgave for person") {
                    createPersonoppgaver(
                        ubehandletVurderStansOppgave,
                        ubehandletVurderStansOppgave.copy(
                            uuid = UUID.randomUUID(),
                            referanseUuid = UUID.randomUUID(),
                        )
                    )

                    kafkaConsumer.mockPollConsumerRecords(
                        recordValue = vurderingUnntak,
                        topic = topic,
                    )

                    assertFailsWith(IllegalStateException::class) {
                        aktivitetskravVurderingConsumer.pollAndProcessRecords(
                            kafkaConsumer = kafkaConsumer,
                        )
                    }
                }
            }
        }
    }
})
