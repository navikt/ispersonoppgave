package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravVurdering
import no.nav.syfo.aktivitetskrav.kafka.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.kafka.domain.VarselType
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.metric.COUNT_AKTIVITETSKRAV_EXPIRED_VARSEL_PERSON_OPPGAVE_CREATED
import no.nav.syfo.metric.COUNT_PERSONOPPGAVE_UPDATED_FROM_AKTIVITETSKRAV_VURDERING
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.behandleAndReadyForPublish
import no.nav.syfo.personoppgave.domain.toPersonOppgaver
import no.nav.syfo.personoppgave.getUbehandledePersonOppgaver
import no.nav.syfo.personoppgave.updatePersonoppgaveSetBehandlet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class VurderStansService(
    private val database: DatabaseInterface,
) {
    fun processAktivitetskravExpiredVarsel(expiredVarselList: List<ExpiredVarsel>) {
        database.connection.use { connection ->
            expiredVarselList.forEach { expiredVarsel ->
                log.info("Received aktivitetskrav expired varsel with uuid=${expiredVarsel.varselUuid} with type ${expiredVarsel.varselType}")
                if (expiredVarsel.varselType == VarselType.FORHANDSVARSEL_STANS_AV_SYKEPENGER) {
                    val existingUbehandledePersonOppgaver = connection.getUbehandledePersonOppgaver(
                        personIdent = expiredVarsel.personIdent,
                        personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                    )
                    if (existingUbehandledePersonOppgaver.isEmpty()) {
                        connection.createPersonOppgave(
                            referanseUuid = expiredVarsel.varselUuid,
                            personIdent = expiredVarsel.personIdent,
                            personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                            publish = true, // cronjob will publish
                        )
                        COUNT_AKTIVITETSKRAV_EXPIRED_VARSEL_PERSON_OPPGAVE_CREATED.increment()
                    } else {
                        log.info("Personoppgave already exists for uuid=${expiredVarsel.varselUuid} with type ${expiredVarsel.varselType}")
                    }
                }
            }
            connection.commit()
        }
    }

    fun processAktivitetskravVurdering(aktivitetskravVurderinger: List<AktivitetskravVurdering>) {
        database.connection.use { connection ->
            aktivitetskravVurderinger.forEach { vurdering ->
                log.info("Received aktivitetskravVurdering with uuid=${vurdering.uuid} and status=${vurdering.status}")
                val ubehandledeVurderStansOppgaver = connection.getUbehandledePersonOppgaver(
                    personIdent = vurdering.personIdent,
                    personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                ).toPersonOppgaver()
                if (ubehandledeVurderStansOppgaver.size > 1) throw IllegalStateException("Cannot have more than one ubehandlet AKTIVITETSKRAV_VURDER_STANS oppgave per personident")

                val vurderStansOppgave = ubehandledeVurderStansOppgaver.firstOrNull()
                if (
                    vurderStansOppgave != null &&
                    vurdering.isFinalVurdering() &&
                    vurdering happenedAfter vurderStansOppgave
                ) {
                    val behandletOppgave = vurderStansOppgave.behandleAndReadyForPublish(veilederIdent = vurdering.vurdertAv)
                    connection.updatePersonoppgaveSetBehandlet(behandletOppgave)
                    COUNT_PERSONOPPGAVE_UPDATED_FROM_AKTIVITETSKRAV_VURDERING.increment()
                }
            }
            connection.commit()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
