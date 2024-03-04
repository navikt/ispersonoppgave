package no.nav.syfo.arbeidsuforhet.kafka

import io.mockk.*
import no.nav.syfo.arbeidsuforhet.kafka.ExpiredForhandsvarselConsumer.Companion.ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_TOPIC
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateExpiredForhandsvarsel
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ExpiredForhandsvarselConsumerSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment()
    val database = externalMockEnvironment.database
    val kafkaConsumer = mockk<KafkaConsumer<String, ExpiredForhandsvarsel>>()

    val expiredForhandsvarselConsumer = ExpiredForhandsvarselConsumer(
        kafkaEnvironment = externalMockEnvironment.environment.kafka,
        applicationState = externalMockEnvironment.applicationState
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
        it("consumes expired forhandsvarsel") {
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
        }
    }
})
