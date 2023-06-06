package no.nav.syfo.behandlerdialog.kafka

import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KMeldingDTODeserializer : Deserializer<KMeldingDTO> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KMeldingDTO =
        mapper.readValue(data, KMeldingDTO::class.java)
}
