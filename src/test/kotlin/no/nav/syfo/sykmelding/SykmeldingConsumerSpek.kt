package no.nav.syfo.sykmelding

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKafkaSykmelding
import no.nav.syfo.testutil.getDuplicateCount
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import no.nav.syfo.testutil.updateCreatedAt
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.OffsetDateTime
import java.util.*

class SykmeldingConsumerSpek : Spek({
    describe("SykmeldingConsumer") {
        val topic = SYKMELDING_TOPIC
        val externalMockEnvironment = ExternalMockEnvironment()
        val database = externalMockEnvironment.database
        val kafkaConsumer = mockk<KafkaConsumer<String, ReceivedSykmeldingDTO>>()
        val kafkaSykmeldingConsumer = KafkaSykmeldingConsumer(database = database, personOppgaveRepository = PersonOppgaveRepository(database = database))

        beforeEachTest {
            every { kafkaConsumer.commitSync() } returns Unit
        }

        afterEachTest {
            database.dropData()
            clearMocks(kafkaConsumer)
        }

        describe("Consume sykmelding") {
            it("Creates oppgave if beskrivBistand has text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = "Bistand påkrevet",
                    )
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgave = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }.first()
                personOppgave.personIdent.value shouldBeEqualTo sykmelding.personNrPasient
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
                personOppgave.behandletTidspunkt shouldBe null
                personOppgave.referanseUuid shouldBeEqualTo sykmeldingId
            }
            it("Creates oppgave if tiltakNAV has text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    tiltakNAV = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgave = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }.first()
                personOppgave.personIdent.value shouldBeEqualTo sykmelding.personNrPasient
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
                personOppgave.behandletTidspunkt shouldBe null
                personOppgave.referanseUuid shouldBeEqualTo sykmeldingId
            }
            it("Creates oppgave if andreTiltak has text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgave = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }.first()
                personOppgave.personIdent.value shouldBeEqualTo sykmelding.personNrPasient
                personOppgave.publish shouldBeEqualTo true
                personOppgave.type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
                personOppgave.behandletTidspunkt shouldBe null
                personOppgave.referanseUuid shouldBeEqualTo sykmeldingId
            }
            it("Does create oppgave if andreTiltak has text but duplicate from previous sykmelding") {
                val referanseUUID = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = referanseUUID,
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )
                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).size shouldBeEqualTo 1

                database.getDuplicateCount(referanseUUID) shouldBeEqualTo 0

                val sykmeldingNext = generateKafkaSykmelding(
                    sykmeldingId = UUID.randomUUID(),
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmeldingNext,
                    topic = topic,
                )
                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                val personoppgaver = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                )
                personoppgaver.size shouldBeEqualTo 2
                val latestPersonoppgave = personoppgaver.find {
                    it.referanseUuid.toString() == sykmeldingNext.sykmelding.id
                }
                latestPersonoppgave!!.duplikatReferanseUuid shouldBeEqualTo referanseUUID
                database.getDuplicateCount(referanseUUID) shouldBeEqualTo 1
            }
            it("Creates oppgave if andreTiltak has text and duplicate from previous old sykmelding") {
                val referanseUUID = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = referanseUUID,
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )
                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                database.updateCreatedAt(referanseUUID, OffsetDateTime.now().minusMonths(7))

                val sykmeldingNext = generateKafkaSykmelding(
                    sykmeldingId = UUID.randomUUID(),
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmeldingNext,
                    topic = topic,
                )
                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).size shouldBeEqualTo 2
            }
            it("creates one oppgave if more than one relevant field has text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                    tiltakNAV = "Jeg synes NAV skal gjøre dette også",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgaver = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }
                personOppgaver.size shouldBeEqualTo 1
                personOppgaver.first().type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
            }
            it("Creates oppgave if andreTiltak has relevant text and tiltakNAV has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    andreTiltak = "Jeg synes NAV skal gjøre dette",
                    tiltakNAV = "-",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgaver = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }
                personOppgaver.size shouldBeEqualTo 1
                personOppgaver.first().type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
            }
            it("Creates oppgave if meldingTilNAV has relevant text and andreTiltak has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = "Sjekk ut saken",
                    ),
                    andreTiltak = ".",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgaver = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }
                personOppgaver.size shouldBeEqualTo 1
                personOppgaver.first().type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
            }
            it("Creates oppgave if tiltakNAV has relevant text and meldingTilNAV has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = "nei",
                    ),
                    tiltakNAV = "Jeg synes NAV skal gjøre dette",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                val personOppgaver = database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).map { it.toPersonOppgave() }
                personOppgaver.size shouldBeEqualTo 1
                personOppgaver.first().type shouldBeEqualTo PersonOppgaveType.BEHANDLER_BER_OM_BISTAND
            }
            it("Does not create oppgave if meldingTilNAV has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = ".",
                    ),
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
            it("Does not create oppgave if tiltakNAV has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    tiltakNAV = "-",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
            it("Does not create oppgave if andreTiltak has irrelevant text") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                    tiltakNAV = "nei",
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
            it("Does not create oppgave if meldingTilNAV, tiltakNAV, and andreTiltak is null") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = null,
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
            it("Does not create oppgave if beskrivBistand is null") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = null,
                    )
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
            it("Does not create oppgave if beskrivBistand is empty") {
                val sykmeldingId = UUID.randomUUID()
                val sykmelding = generateKafkaSykmelding(
                    sykmeldingId = sykmeldingId,
                    meldingTilNAV = MeldingTilNAV(
                        bistandUmiddelbart = false,
                        beskrivBistand = "",
                    )
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = sykmelding,
                    topic = topic,
                )

                kafkaSykmeldingConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )
                verify(exactly = 1) {
                    kafkaConsumer.commitSync()
                }

                database.getPersonOppgaver(
                    personIdent = PersonIdent(sykmelding.personNrPasient),
                ).shouldBeEmpty()
            }
        }
    }
})
