package no.nav.syfo.dialogmotestatusendring

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.kafka.KafkaDialogmoteStatusendring
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*

class DialogmoteStatusendringTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var kafkaConsumer: KafkaConsumer<String, KDialogmoteStatusEndring>
    private lateinit var kafkaDialogmoteStatusendring: KafkaDialogmoteStatusendring

    @BeforeEach
    fun setup() {
        database = externalMockEnvironment.database
        kafkaConsumer = mockk(relaxed = true)
        kafkaDialogmoteStatusendring = KafkaDialogmoteStatusendring(database = database)
        every { kafkaConsumer.commitSync() } returns Unit
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    @Test
    fun `stores dialogmotesvar from kafka in database`() {
        val moteUuid = UUID.randomUUID()
        val instantNow = Instant.now()
        val offsetNow = OffsetDateTime.ofInstant(instantNow, ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
        val kDialogmoteStatusendring = KDialogmoteStatusEndring.newBuilder()
            .setDialogmoteUuid(moteUuid.toString())
            .setPersonIdent(UserConstants.ARBEIDSTAKER_FNR.value)
            .setStatusEndringType(DialogmoteStatusendringType.AVLYST.name)
            .setDialogmoteTidspunkt(instantNow)
            .setStatusEndringTidspunkt(instantNow)
            .setVirksomhetsnummer(UserConstants.VIRKSOMHETSNUMMER.value)
            .setEnhetNr(UserConstants.NAV_ENHET)
            .setTilfelleStartdato(instantNow)
            .setNavIdent(UserConstants.VEILEDER_IDENT)
            .setArbeidstaker(true)
            .setArbeidsgiver(true)
            .setSykmelder(true).build()

        kafkaConsumer.mockPollConsumerRecords(recordValue = kDialogmoteStatusendring, recordKey = moteUuid.toString())

        kafkaDialogmoteStatusendring.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val allPDialogmoteStatusendring = database.connection.use { connection ->
            connection.getDialogmoteStatusendring(moteUuid = moteUuid)
        }
        assertEquals(1, allPDialogmoteStatusendring.size)
        val pDialogmoteStatusendring = allPDialogmoteStatusendring[0]
        assertEquals(moteUuid.toString(), pDialogmoteStatusendring.moteUuid)
        assertEquals(offsetNow, pDialogmoteStatusendring.endringTidspunkt)
        assertEquals(DialogmoteStatusendringType.AVLYST.name, pDialogmoteStatusendring.type)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, pDialogmoteStatusendring.arbeidstakerIdent)
        assertEquals(UserConstants.VEILEDER_IDENT, pDialogmoteStatusendring.veilederIdent)
    }
}
