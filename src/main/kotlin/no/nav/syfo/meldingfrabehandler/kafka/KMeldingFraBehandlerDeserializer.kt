package no.nav.syfo.meldingfrabehandler.kafka

import no.nav.syfo.meldingfrabehandler.domain.KMeldingFraBehandler
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Deserializer

class KMeldingFraBehandlerDeserializer : Deserializer<KMeldingFraBehandler> {
    private val mapper = configuredJacksonMapper()
    override fun deserialize(topic: String, data: ByteArray): KMeldingFraBehandler =
        mapper.readValue(data, KMeldingFraBehandler::class.java)
}
