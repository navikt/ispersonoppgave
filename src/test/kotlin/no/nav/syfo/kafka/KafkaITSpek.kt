package no.nav.syfo.kafka

import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.OPPFOLGINGSPLAN_LPS_NAV_TOPIC
import no.nav.syfo.testutil.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

class KafkaITSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment(
        withSchemaRegistry = true,
    )

    val consumerOppfolgingsplanLPS = testOppfolgingsplanLPSConsumer(
        externalMockEnvironment = externalMockEnvironment,
    )

    beforeGroup {
        externalMockEnvironment.startExternalMocks()
    }

    afterGroup {
        externalMockEnvironment.stopExternalMocks()
    }

    describe("Produce and consume messages from topic") {
        it("Topic $OPPFOLGINGSPLAN_LPS_NAV_TOPIC") {
            val messages: ArrayList<KOppfolgingsplanLPSNAV> = arrayListOf()
            consumerOppfolgingsplanLPS.poll(Duration.ofMillis(5000)).forEach {
                if (it != null) {
                    val consumedOppfolgingsplanLPSNAV = it.value()
                    messages.add(consumedOppfolgingsplanLPSNAV)
                }
            }
            messages.size shouldBeEqualTo 0
        }
    }
})
