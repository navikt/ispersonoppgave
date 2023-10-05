package no.nav.syfo.dialogmotesvar

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.dialogmotesvar.kafka.KafkaDialogmotesvarConsumer
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

class DialogmotesvarSpek : Spek({
    describe("Handling dialogmotesvar topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KDialogmotesvar>>()
            val kafkaDialogmotesvarConsumer =
                KafkaDialogmotesvarConsumer(database = database, cutoffDate = LocalDate.now().minusDays(20))

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.dropData()
            }

            it("stores dialogmøtesvar from kafka in database") {
                val moteUuid = UUID.randomUUID()
                val offsetNow = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
                val kDialogmotesvar = generateKDialogmotesvar().copy(
                    brevSentAt = offsetNow,
                    svarReceivedAt = offsetNow,
                )
                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = kDialogmotesvar,
                    recordKey = moteUuid.toString(),
                )

                kafkaDialogmotesvarConsumer.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val allPMotesvar = database.connection.getDialogmotesvar(
                    moteUuid = moteUuid,
                )
                allPMotesvar.size shouldBeEqualTo 1
                val pMotesvar = allPMotesvar[0]
                pMotesvar.moteUuid shouldBeEqualTo moteUuid.toString()
                pMotesvar.arbeidstakerIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR.value
                pMotesvar.svarType shouldBeEqualTo DialogmoteSvartype.KOMMER.name
                pMotesvar.senderType shouldBeEqualTo SenderType.ARBEIDSTAKER.name
                pMotesvar.brevSentAt.isEqual(offsetNow) shouldBe true
                pMotesvar.svarReceivedAt.isEqual(offsetNow) shouldBe true
            }
        }
    }
})
