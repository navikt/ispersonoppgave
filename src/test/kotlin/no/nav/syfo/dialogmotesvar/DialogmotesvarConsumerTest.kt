package no.nav.syfo.dialogmotesvar

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.dialogmotesvar.domain.KDialogmotesvar
import no.nav.syfo.dialogmotesvar.domain.SenderType
import no.nav.syfo.dialogmotesvar.kafka.KafkaDialogmotesvarConsumer
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKDialogmotesvar
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

class DialogmotesvarConsumerTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaConsumer: KafkaConsumer<String, KDialogmotesvar> = mockk(relaxed = true)
    private val kafkaDialogmotesvarConsumer = KafkaDialogmotesvarConsumer(
        database = database,
        cutoffDate = LocalDate.now().minusDays(20),
    )

    @BeforeEach
    fun setup() {
        clearMocks(kafkaConsumer)
        every { kafkaConsumer.commitSync() } returns Unit
        database.dropData()
    }

    @Test
    fun `stores dialogmotesvar from kafka in database`() {
        val moteUuid = UUID.randomUUID()
        val offsetNow = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val kDialogmotesvar = generateKDialogmotesvar().copy(
            brevSentAt = offsetNow,
            svarReceivedAt = offsetNow,
        )
        kafkaConsumer.mockPollConsumerRecords(recordValue = kDialogmotesvar, recordKey = moteUuid.toString())

        kafkaDialogmotesvarConsumer.pollAndProcessRecords(kafkaConsumer = kafkaConsumer)

        val allPMotesvar = database.connection.use { connection ->
            connection.getDialogmotesvar(moteUuid = moteUuid)
        }
        assertEquals(1, allPMotesvar.size)
        val pMotesvar = allPMotesvar[0]
        assertEquals(moteUuid.toString(), pMotesvar.moteUuid)
        assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, pMotesvar.arbeidstakerIdent)
        assertEquals(DialogmoteSvartype.KOMMER.name, pMotesvar.svarType)
        assertEquals(SenderType.ARBEIDSTAKER.name, pMotesvar.senderType)
        assertTrue(pMotesvar.brevSentAt.isEqual(offsetNow))
        assertTrue(pMotesvar.svarReceivedAt.isEqual(offsetNow))
    }
}
