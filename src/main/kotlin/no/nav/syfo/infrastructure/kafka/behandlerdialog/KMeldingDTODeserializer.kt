package no.nav.syfo.infrastructure.kafka.behandlerdialog

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KMeldingDTODeserializer : Deserializer<KMeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KMeldingDTO =
        mapper.readValue(data, KMeldingDTO::class.java)
}
