package no.nav.syfo.arbeidsuforhet

import io.micrometer.core.instrument.Counter
import no.nav.syfo.arbeidsuforhet.kafka.ArbeidsuforhetVurdering
import no.nav.syfo.arbeidsuforhet.kafka.ExpiredForhandsvarsel
import no.nav.syfo.arbeidsuforhet.kafka.behandler
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.behandleAndReadyForPublish
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VurderAvslagService(private val database: DatabaseInterface, private val personOppgaveRepository: PersonOppgaveRepository) {

    fun processExpiredForhandsvarsel(expiredForhandsvarselList: List<ExpiredForhandsvarsel>) {
        database.connection.use { connection ->
            expiredForhandsvarselList.forEach {
                log.info("Received arbeidsuforhet expired forhandsvarsel with uuid=${it.uuid}")
                val personoppgave = it.toPersonoppgave()
                val ubehandledePersonOppgaver = personOppgaveRepository.getUbehandledePersonoppgaver(
                    personIdent = personoppgave.personIdent,
                    type = personoppgave.type,
                    connection = connection,
                )
                if (ubehandledePersonOppgaver.isEmpty()) {
                    personOppgaveRepository.createPersonoppgave(
                        personOppgave = personoppgave,
                        connection = connection,
                    )
                    COUNT_ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_PERSON_OPPGAVE_CREATED.increment()
                } else {
                    log.error("Ubehandlet personoppgave of type ${personoppgave.type} already exists for person - forhandsvarsel with uuid=${it.uuid}")
                }
            }
            connection.commit()
        }
    }

    fun processArbeidsuforhetVurdering(vurderingList: List<ArbeidsuforhetVurdering>) {
        database.connection.use { connection ->
            vurderingList.forEach { vurdering ->
                log.info("Received vurdering with uuid=${vurdering.uuid} and type=${vurdering.type}")
                val oppgaveType = PersonOppgaveType.ARBEIDSUFORHET_VURDER_AVSLAG
                val ubehandledeOppgaver = personOppgaveRepository.getUbehandledePersonoppgaver(
                    connection = connection,
                    personIdent = PersonIdent(vurdering.personident),
                    type = oppgaveType,
                )
                if (ubehandledeOppgaver.size > 1) {
                    log.error("Found more than one ubehandlet $oppgaveType oppgave for person")
                }

                ubehandledeOppgaver.forEach { oppgave ->
                    if (vurdering behandler oppgave) {
                        val behandletOppgave = oppgave.behandleAndReadyForPublish(veilederIdent = vurdering.veilederident)
                        personOppgaveRepository.updatePersonoppgaveBehandlet(personOppgave = behandletOppgave, connection = connection)
                        COUNT_PERSONOPPGAVE_UPDATED_FROM_ARBEIDSUFORHET_VURDERING.increment()
                    }
                }
            }
            connection.commit()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        private const val ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_PERSON_OPPGAVE_CREATED =
            "${METRICS_NS}_arbeidsuforhet_expired_forhandsvarsel_person_oppgave_created_count"
        private val COUNT_ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_PERSON_OPPGAVE_CREATED: Counter =
            Counter.builder(ARBEIDSUFORHET_EXPIRED_FORHANDSVARSEL_PERSON_OPPGAVE_CREATED)
                .description("Counts the number of personoppgaver created from arbeidsuforhet expired forhandsvarsel")
                .register(METRICS_REGISTRY)
        private const val PERSONOPPGAVE_UPDATED_FROM_ARBEIDSUFORHET_VURDERING =
            "${METRICS_NS}_arbeidsuforhet_vurdering_mottatt_count"
        val COUNT_PERSONOPPGAVE_UPDATED_FROM_ARBEIDSUFORHET_VURDERING: Counter =
            Counter.builder(PERSONOPPGAVE_UPDATED_FROM_ARBEIDSUFORHET_VURDERING)
                .description("Counts the number of personoppgaver updated from incoming arbeidsuforhet vurdering")
                .register(METRICS_REGISTRY)
    }
}
