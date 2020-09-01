package no.nav.syfo.kafka

import no.nav.common.KafkaEnvironment
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.testutil.generateKOppfolgingsplanLPSNAV
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.Duration
import java.util.*

object KafkaITSpek : Spek({

    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        withSchemaRegistry = true,
        topicNames = listOf(
            OPPFOLGINGSPLAN_LPS_NAV_TOPIC
        )
    )
    val credentials = VaultSecrets(
        "",
        ""
    )
    val env = Environment(
        applicationPort = getRandomPort(),
        applicationThreads = 1,
        applicationName = "ispersonoppgave",
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        databaseName = "ispersonoppgave",
        ispersonoppgaveDBURL = "12314.adeo.no",
        mountPathVault = "vault.adeo.no"
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
        put("schema.registry.url", embeddedEnvironment.schemaRegistry!!.url)
    }

    val consumerPropertiesOppfolgingsplanLPS = kafkaConsumerConfig(env, credentials)
        .overrideForTest()

    val producerPropertiesOppfolgingsplanLPS = kafkaConsumerConfig(env, credentials)
        .overrideForTest()
        .apply {
            put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
            put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
        }
    val producerOppfolgingsplanLPS = KafkaProducer<String, KOppfolgingsplanLPSNAV>(producerPropertiesOppfolgingsplanLPS)

    val consumerOppfolgingsplanLPS = KafkaConsumer<String, KOppfolgingsplanLPSNAV>(consumerPropertiesOppfolgingsplanLPS)
    consumerOppfolgingsplanLPS.subscribe(listOf(OPPFOLGINGSPLAN_LPS_NAV_TOPIC))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Produce and consume messages from topic") {
        it("Topic $OPPFOLGINGSPLAN_LPS_NAV_TOPIC") {
            val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
            producerOppfolgingsplanLPS.send(SyfoProducerRecord(
                OPPFOLGINGSPLAN_LPS_NAV_TOPIC,
                UUID.randomUUID().toString(),
                kOppfolgingsplanLPSNAV
            ))

            val messages: ArrayList<KOppfolgingsplanLPSNAV> = arrayListOf()
            consumerOppfolgingsplanLPS.poll(Duration.ofMillis(5000)).forEach {
                val consumedOppfolgingsplanLPSNAV = it.value()
                messages.add(consumedOppfolgingsplanLPSNAV)
            }
            messages.size shouldEqual 1
            messages.first() shouldEqual kOppfolgingsplanLPSNAV
        }
    }
})
