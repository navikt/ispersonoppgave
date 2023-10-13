package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.kafka.domain.ExpiredVarsel
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class ExpiredVarselDeserializer : Deserializer<ExpiredVarsel> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ExpiredVarsel =
        mapper.readValue(data, ExpiredVarsel::class.java)
}
