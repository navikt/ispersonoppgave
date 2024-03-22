package no.nav.syfo.arbeidsuforhet.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class ArbeidsuforhetVurderingDeserializer : Deserializer<ArbeidsuforhetVurdering> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): ArbeidsuforhetVurdering =
        mapper.readValue(data, ArbeidsuforhetVurdering::class.java)
}
