package no.nav.syfo.personoppgavehendelse

import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.personoppgavehendelse")

const val PERSONOPPGAVEHENDELSE_TOPIC = "teamsykefravr.personoppgavehendelse"

class PersonoppgavehendelseProducer(
    private val producer: KafkaProducer<String, KPersonoppgavehendelse>
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
            producer.send(record).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send KPersonoppgavehendelse with id {}: ${e.message}",
                personoppgaveId,
            )
            throw e
        }
    }
}
