package no.nav.syfo.dialogmotesvar

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dialogmotesvar.domain.*
import no.nav.syfo.dialogmotesvar.kafka.pollAndProcessDialogmotesvar
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
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
            val kafkaDialogmotesvar = mockk<KafkaConsumer<String, KDialogmotesvar>>()

            beforeEachTest {
                every { kafkaDialogmotesvar.commitSync() } returns Unit
            }

            afterEachTest {
                database.connection.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            it("stores dialogmøtesvar from kafka in database") {
                val moteUuid = UUID.randomUUID()
                val offsetNow = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
                val kDialogmotesvar = generateKDialogmotesvar().copy(
                    brevSentAt = offsetNow,
                    svarReceivedAt = offsetNow,
                )
                mockReceiveDialogmotesvar(
                    kDialogmotesvar = kDialogmotesvar,
                    mockKafkaDialogmotesvar = kafkaDialogmotesvar,
                    moteUuid = moteUuid,
                )

                pollAndProcessDialogmotesvar(
                    database = database,
                    kafkaConsumer = kafkaDialogmotesvar,
                    cutoffDate = LocalDate.now().minusDays(20),
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
fun mockReceiveDialogmotesvar(
    kDialogmotesvar: KDialogmotesvar,
    mockKafkaDialogmotesvar: KafkaConsumer<String, KDialogmotesvar>,
    moteUuid: UUID,
) {
    every { mockKafkaDialogmotesvar.poll(any<Duration>()) } returns ConsumerRecords(
        mapOf(
            dialogmotesvarTopicPartition() to listOf(
                dialogmotesvarRecord(
                    kDialogmotesvar,
                    moteUuid,
                ),
            )
        )
    )
}

fun dialogmotesvarTopicPartition() = TopicPartition(
    "topicnavn",
    0
)

fun dialogmotesvarRecord(
    kDialogmotesvar: KDialogmotesvar,
    moteUuid: UUID,
) = ConsumerRecord(
    "topicnavn",
    0,
    1,
    moteUuid.toString(),
    kDialogmotesvar
)
