package no.nav.syfo.infrastructure.kafka.oppgavehendelse

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.PersonoppgavehendelseType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class PersonoppgavehendelseProducer(
    private val producer: KafkaProducer<String, KPersonoppgavehendelse>,
) {
    fun sendPersonoppgavehendelse(
        hendelsetype: PersonoppgavehendelseType,
        personIdent: PersonIdent,
        personoppgaveId: UUID,
    ) {
        val kPersonoppgavehendelse = KPersonoppgavehendelse(
            personIdent.value,
            hendelsetype.name
        )

        try {
            log.info("Sending personoppgavehendelse of type $hendelsetype, personoppgaveId: $personoppgaveId")
            val record = ProducerRecord(
                PERSONOPPGAVEHENDELSE_TOPIC,
                personoppgaveId.toString(),
                kPersonoppgavehendelse,
            )
            producer.send(record).also {
                it.get()
            }
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KPersonoppgavehendelse with id {}: ${e.message}",
                personoppgaveId,
            )
            throw e
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PersonoppgavehendelseProducer::class.java)
        private const val PERSONOPPGAVEHENDELSE_TOPIC = "teamsykefravr.personoppgavehendelse"
    }
}
