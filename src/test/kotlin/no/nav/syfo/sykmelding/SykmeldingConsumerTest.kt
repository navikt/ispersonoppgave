package no.nav.syfo.sykmelding

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKafkaSykmelding
import no.nav.syfo.testutil.getDuplicateCount
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import no.nav.syfo.testutil.updateCreatedAt
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.OffsetDateTime
import java.util.*

class SykmeldingConsumerTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var kafkaConsumer: KafkaConsumer<String, ReceivedSykmeldingDTO>
    private lateinit var kafkaSykmeldingConsumer: KafkaSykmeldingConsumer
    private lateinit var topic: String

    @BeforeEach
    fun setup() {
        database = externalMockEnvironment.database
        kafkaConsumer = mockk(relaxed = true)
        kafkaSykmeldingConsumer = KafkaSykmeldingConsumer(database = database, personOppgaveRepository = PersonOppgaveRepository(database = database))
        topic = SYKMELDING_TOPIC
        every { kafkaConsumer.commitSync() } returns Unit
    }

    @AfterEach
    fun teardown() {
        database.dropData()
        clearMocks(kafkaConsumer)
    }

    @Test
    fun `Creates oppgave if beskrivBistand has text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(
                bistandUmiddelbart = false,
                beskrivBistand = "Bistand påkrevet",
            )
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgave = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }.first()
        assertEquals(sykmelding.personNrPasient, personOppgave.personIdent.value)
        assertTrue(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgave.type)
        assertNull(personOppgave.behandletTidspunkt)
        assertEquals(sykmeldingId, personOppgave.referanseUuid)
    }

    @Test
    fun `Creates oppgave if tiltakNAV has text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
            tiltakNAV = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgave = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }.first()
        assertEquals(sykmelding.personNrPasient, personOppgave.personIdent.value)
        // assertTrue(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgave.type)
        assertNull(personOppgave.behandletTidspunkt)
        assertEquals(sykmeldingId, personOppgave.referanseUuid)
    }

    @Test
    fun `Creates oppgave if andreTiltak has text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgave = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }.first()
        assertEquals(sykmelding.personNrPasient, personOppgave.personIdent.value)
        assertTrue(personOppgave.publish)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgave.type)
        assertNull(personOppgave.behandletTidspunkt)
        assertEquals(sykmeldingId, personOppgave.referanseUuid)
    }

    @Test
    fun `Marks second duplicate sykmelding oppgave with duplikatReferanseUuid`() {
        val referanseUUID = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = referanseUUID,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        assertEquals(1, database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).size)
        assertEquals(0, database.getDuplicateCount(referanseUUID))
        val sykmeldingNext = generateKafkaSykmelding(
            sykmeldingId = UUID.randomUUID(),
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmeldingNext, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        val personoppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient))
        assertEquals(2, personoppgaver.size)
        val latestPersonoppgave = personoppgaver.find { it.referanseUuid.toString() == sykmeldingNext.sykmelding.id }!!
        assertEquals(referanseUUID, latestPersonoppgave.duplikatReferanseUuid)
        assertEquals(1, database.getDuplicateCount(referanseUUID))
    }

    @Test
    fun `Does not mark as duplicate if one text differs`() {
        val referanseUUID = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = referanseUUID,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        val sykmeldingNext = generateKafkaSykmelding(
            sykmeldingId = UUID.randomUUID(),
            meldingTilNAV = MeldingTilNAV(true, "Bistand!"),
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmeldingNext, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        val personoppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient))
        assertEquals(2, personoppgaver.size)
        val latestPersonoppgave = personoppgaver.find { it.referanseUuid.toString() == sykmeldingNext.sykmelding.id }!!
        assertNull(latestPersonoppgave.duplikatReferanseUuid)
        assertEquals(0, database.getDuplicateCount(referanseUUID))
    }

    @Test
    fun `Creates duplicate if previous duplicate older than 6 months is ignored`() {
        val referanseUUID = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = referanseUUID,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        database.updateCreatedAt(referanseUUID, OffsetDateTime.now().minusMonths(7))
        val sykmeldingNext = generateKafkaSykmelding(
            sykmeldingId = UUID.randomUUID(),
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmeldingNext, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        val personoppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient))
        assertEquals(2, personoppgaver.size)
        assertFalse(personoppgaver.any { it.duplikatReferanseUuid != null })
    }

    @Test
    fun `Creates one oppgave if more than one relevant field has text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
            tiltakNAV = "Jeg synes NAV skal gjøre dette også",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }
        assertEquals(1, personOppgaver.size)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgaver.first().type)
    }

    @Test
    fun `Creates oppgave when only andreTiltak relevant`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
            andreTiltak = "Jeg synes NAV skal gjøre dette",
            tiltakNAV = "-",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }
        assertEquals(1, personOppgaver.size)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgaver.first().type)
    }

    @Test
    fun `Creates oppgave when only meldingTilNAV relevant`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = "Sjekk ut saken"),
            andreTiltak = ".",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }
        assertEquals(1, personOppgaver.size)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgaver.first().type)
    }

    @Test
    fun `Creates oppgave when only tiltakNAV relevant`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = "nei"),
            tiltakNAV = "Jeg synes NAV skal gjøre dette",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        val personOppgaver = database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).map { it.toPersonOppgave() }
        assertEquals(1, personOppgaver.size)
        assertEquals(PersonOppgaveType.BEHANDLER_BER_OM_BISTAND, personOppgaver.first().type)
    }

    @Test
    fun `Does not create oppgave if meldingTilNAV has irrelevant text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = "."),
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        assertTrue(database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).isEmpty())
    }

    @Test
    fun `Does not create oppgave if tiltakNAV has irrelevant text`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
            tiltakNAV = "-",
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        assertTrue(database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).isEmpty())
    }

    @Test
    fun `Does not create oppgave if all relevant fields null`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = null,
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        assertTrue(database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).isEmpty())
    }

    @Test
    fun `Does not create oppgave if beskrivBistand is null`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = null),
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        assertTrue(database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).isEmpty())
    }

    @Test
    fun `Does not create oppgave if beskrivBistand is empty`() {
        val sykmeldingId = UUID.randomUUID()
        val sykmelding = generateKafkaSykmelding(
            sykmeldingId = sykmeldingId,
            meldingTilNAV = MeldingTilNAV(bistandUmiddelbart = false, beskrivBistand = ""),
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = sykmelding, topic = topic)
        kafkaSykmeldingConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)
        verify(exactly = 1) { kafkaConsumer.commitSync() }
        assertTrue(database.getPersonOppgaver(PersonIdent(sykmelding.personNrPasient)).isEmpty())
    }
}
