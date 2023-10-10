package no.nav.syfo.aktivitetskrav.kafka

import no.nav.syfo.aktivitetskrav.kafka.domain.KafkaAktivitetskravVurdering
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class AktivitetskravVurderingDeserializer : Deserializer<KafkaAktivitetskravVurdering> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KafkaAktivitetskravVurdering =
        mapper.readValue(data, KafkaAktivitetskravVurdering::class.java)
}
