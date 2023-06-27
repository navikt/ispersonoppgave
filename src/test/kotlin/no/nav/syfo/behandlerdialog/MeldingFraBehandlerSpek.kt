package no.nav.syfo.behandlerdialog

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.KMeldingDTO
import no.nav.syfo.behandlerdialog.kafka.KafkaMeldingFraBehandler
import no.nav.syfo.behandlerdialog.kafka.KafkaUbesvartMelding
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.domain.toPersonOppgave
import no.nav.syfo.personoppgave.getPersonOppgaveByReferanseUuid
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
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
            val personOppgaveService = PersonOppgaveService(
                database = database,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )
            val meldingFraBehandlerService = MeldingFraBehandlerService(
                database = database,
                personOppgaveService = personOppgaveService,
            )
            val kafkaMeldingFraBehandler = KafkaMeldingFraBehandler(
                database = database,
                meldingFraBehandlerService = meldingFraBehandlerService,
            )

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
                justRun { personoppgavehendelseProducer.sendPersonoppgavehendelse(any(), any(), any()) }
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

            it("stores melding fra behandler from kafka in database and publish as new oppgave") {
                val referanseUuid = UUID.randomUUID()
                val kMeldingFraBehandler = generateKMeldingDTO(referanseUuid)
                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandler,
                    kafkaConsumer = kafkaConsumer,
                )

                kafkaMeldingFraBehandler.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val personOppgave = database.connection.getPersonOppgaveByReferanseUuid(
                    referanseUuid = referanseUuid,
                )!!.toPersonOppgave()
                personOppgave.publish shouldBeEqualTo false
                personOppgave.type.name shouldBeEqualTo PersonOppgaveType.BEHANDLERDIALOG_SVAR.name

                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                        personIdent = personOppgave.personIdent,
                        personoppgaveId = personOppgave.uuid,
                    )
                }
            }

            it("behandler ubesvart melding if svar received on same melding") {
                val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
                val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)
                val referanseUuid = UUID.randomUUID()
                val kUbesvartMeldingDTO = generateKMeldingDTO(uuid = referanseUuid)
                val kMeldingFraBehandlerDTO = generateKMeldingDTO(parentRef = referanseUuid)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kUbesvartMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )

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

                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_MOTTATT,
                        personIdent = personoppgaveUbesvart.personIdent,
                        personoppgaveId = personoppgaveUbesvart.uuid,
                    )
                }
                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET,
                        personIdent = personoppgaveUbesvart.personIdent,
                        personoppgaveId = personoppgaveUbesvart.uuid,
                    )
                }
                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                        personIdent = personoppgaveSvar.personIdent,
                        personoppgaveId = personoppgaveSvar.uuid,
                    )
                }
            }

            it("behandler ubesvart melding if svar received on same melding, but does not publish if there are other ubehandlede ubesvart oppgaver") {
                val ubesvartMeldingService = UbesvartMeldingService(personOppgaveService)
                val kafkaUbesvartMelding = KafkaUbesvartMelding(database, ubesvartMeldingService)
                val referanseUuid = UUID.randomUUID()
                val otherReferanseUuid = UUID.randomUUID()
                val kUbesvartMeldingDTO = generateKMeldingDTO(uuid = referanseUuid)
                val otherKUbesvartMeldingDTO = generateKMeldingDTO(uuid = otherReferanseUuid)
                val kMeldingFraBehandlerDTO = generateKMeldingDTO(parentRef = referanseUuid)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kUbesvartMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)
                mockReceiveMeldingDTO(
                    kMeldingDTO = otherKUbesvartMeldingDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                kafkaUbesvartMelding.pollAndProcessRecords(kafkaConsumer)

                mockReceiveMeldingDTO(
                    kMeldingDTO = kMeldingFraBehandlerDTO,
                    kafkaConsumer = kafkaConsumer,
                )
                kafkaMeldingFraBehandler.pollAndProcessRecords(kafkaConsumer)

                val personoppgaveList = database.getPersonOppgaveList(
                    personIdent = PersonIdent(kUbesvartMeldingDTO.personIdent),
                ).map { it.toPersonOppgave() }
                personoppgaveList.size shouldBeEqualTo 3
                val personoppgaveUbesvart = personoppgaveList.first { it.referanseUuid == referanseUuid }
                val otherPersonoppgaveUbesvart = personoppgaveList.first { it.referanseUuid == otherReferanseUuid }
                val personoppgaveSvar = personoppgaveList.first { it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR }
                personoppgaveUbesvart.behandletTidspunkt shouldNotBeEqualTo null
                personoppgaveUbesvart.behandletVeilederIdent shouldBeEqualTo Constants.SYSTEM_VEILEDER_IDENT
                personoppgaveUbesvart.publish shouldBeEqualTo false
                otherPersonoppgaveUbesvart.behandletTidspunkt shouldBeEqualTo null
                otherPersonoppgaveUbesvart.behandletVeilederIdent shouldBeEqualTo null
                personoppgaveSvar.behandletTidspunkt shouldBeEqualTo null
                personoppgaveSvar.publish shouldBeEqualTo false

                verify(exactly = 0) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET,
                        personIdent = personoppgaveUbesvart.personIdent,
                        personoppgaveId = personoppgaveUbesvart.uuid,
                    )
                }
                verify(exactly = 1) {
                    personoppgavehendelseProducer.sendPersonoppgavehendelse(
                        hendelsetype = PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT,
                        personIdent = personoppgaveSvar.personIdent,
                        personoppgaveId = personoppgaveSvar.uuid,
                    )
                }
            }
        }
    }
})
