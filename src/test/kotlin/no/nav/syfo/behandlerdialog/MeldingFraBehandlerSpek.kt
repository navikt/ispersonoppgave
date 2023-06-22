package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockReceiveMeldingDTO
import no.nav.syfo.util.Constants
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class MeldingFraBehandlerSpek : Spek({
    describe("Handle melding-fra-behandler topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KMeldingDTO>>()
            val personoppgavehendelseProducer = mockk<PersonoppgavehendelseProducer>()
            val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(database = database)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.connection.dropData()
                clearMocks(personoppgavehendelseProducer)
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
            }

            afterGroup {
                externalMockEnvironment.stopExternalMocks()
            }

            it("stores melding fra behandler from kafka in database") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingFraBehandler = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandler,
                    kafkaConsumer = kafkaConsumer,
                )

                kafkaMeldingFraBehandler.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val pPersonOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )
                pPersonOppgave?.publish shouldBeEqualTo false
                pPersonOppgave?.type shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_SVAR.name
            }

            it("behandler ubesvart melding if svar received on same melding") {
                val kafkaUbesvartMelding = KafkaUbesvartMelding(database, personoppgavehendelseProducer)
                val referanseUuid = UUID.randomUUID()
                val kUbesvartMeldingDTO = generateKMeldingDTO(uuid = referanseUuid)
                val kMeldingFraBehandlerDTO = generateKMeldingDTO(parentRef = referanseUuid)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kUbesvartMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
                kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandlerDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaConsumer)

                val personoppgaveList = database.getPersonOppgaveList(
                    personIdent = PersonIdent(kUbesvartMeldingDTO.personIdent),
                ).map { it.toPersonOppgave() }
                personoppgaveList.size shouldBeEqualTo 2
                val personoppgaveUbesvart = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART }
                val personoppgaveSvar = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR }
                personoppgaveUbesvart.behandletTidspunkt shouldNotBeEqualTo null
                personoppgaveUbesvart.behandletVeilederIdent shouldBeEqualTo Constants.SYSTEM_VEILEDER_IDENT
                personoppgaveUbesvart.publish shouldBeEqualTo false
                personoppgaveSvar.behandletTidspunkt shouldBeEqualTo null
                personoppgaveSvar.publish shouldBeEqualTo false
            }
        }
    }
})
