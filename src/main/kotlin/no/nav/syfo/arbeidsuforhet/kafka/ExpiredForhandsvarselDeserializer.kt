package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class ExpiredForhandsvarselDeserializer : Deserializer<ExpiredForhandsvarsel> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ExpiredForhandsvarsel =
        mapper.readValue(data, ExpiredForhandsvarsel::class.java)
}
