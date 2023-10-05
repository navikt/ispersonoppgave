package no.nav.syfo.aktivitetskrav

import no.nav.syfo.aktivitetskrav.domain.ExpiredVarsel
import no.nav.syfo.aktivitetskrav.domain.VarselType
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.metric.COUNT_PERSONOPPGAVEHENDELSE_AKTIVITETSKRAV_EXPIRED_VARSEL_MOTTATT
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.*
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
                    connection.createPersonOppgave(
                        referanseUuid = expiredVarsel.varselUuid,
                        personIdent = expiredVarsel.personIdent,
                        personOppgaveType = PersonOppgaveType.AKTIVITETSKRAV_VURDER_STANS,
                        publish = true, // cronjob will publish
                    )
                    COUNT_PERSONOPPGAVEHENDELSE_AKTIVITETSKRAV_EXPIRED_VARSEL_MOTTATT.increment()
                }
            }
            connection.commit()
        }
    }

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
