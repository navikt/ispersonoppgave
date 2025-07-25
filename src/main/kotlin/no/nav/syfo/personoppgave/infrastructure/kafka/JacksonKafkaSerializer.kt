package no.nav.syfo.personoppgave.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.serialization.Serializer

class JacksonKafkaSerializer : Serializer<Any> {
    private val objectMapper: ObjectMapper = ObjectMapper()

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        objectMapper.apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }

    override fun serialize(topic: String?, data: Any?): ByteArray = objectMapper.writeValueAsBytes(data)

    override fun close() {}
}
