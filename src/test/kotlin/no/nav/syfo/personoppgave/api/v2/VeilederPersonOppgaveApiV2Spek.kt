package no.nav.syfo.personoppgave.api.v2

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.updatePersonOppgaveBehandlet
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testutil.generators.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure
import org.amshove.kluent.shouldBeBefore
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Future

class VeilederPersonOppgaveApiV2Spek : Spek({

    describe(VeilederPersonOppgaveApiV2Spek::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment()
        val database = externalMockEnvironment.database

        val baseUrl = registerVeilederPersonOppgaveApiV2BasePath

        val kafkaProducer = mockk<KafkaProducer<String, KPersonoppgavehendelse>>(relaxed = true)
        val personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)
        val personOppgaveRepository = PersonOppgaveRepository(database = database)

        fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
            application {
                testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    personoppgavehendelseProducer = personoppgavehendelseProducer
                )
            }
            val client = createClient {
                install(ContentNegotiation) {
                    jackson { configure() }
                }
            }

            return client
        }

        beforeEachTest {
            clearMocks(kafkaProducer)
            coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        afterGroup {
            externalMockEnvironment.stopExternalMocks()
        }

        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azureAppClientId,
            issuer = externalMockEnvironment.wellKnownInternADV2Mock.issuer,
            navIdent = VEILEDER_IDENT,
        )

        describe("Get PersonOppgave for PersonIdent") {
            val url = "$baseUrl/personident"

            it("returns status BadRequest if NAV_PERSONIDENT_HEADER is missing") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            it("returns status BadRequest if NAV_PERSONIDENT_HEADER has an invalid Fodselsnummer") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                    }

                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }

            it("returns status Forbidden if Veileder does not have access to request PersonIdent") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1).plus("0"))
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Forbidden
                }
            }

            it("returns status NoContent if there is no PersonOppgaver for PersonIdent") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.NoContent
                }
            }

            it("returns PersonOppgaveList if there is a PersonOppgave with type OppfolgingsplanLPS for PersonIdent") {
                val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                database.connection.use {
                    it.createPersonOppgave(
                        kOppfolgingsplanLPS,
                        personOppgaveType
                    )
                    it.commit()
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val personOppgaveList = response.body<List<PersonOppgaveVeileder>>()
                    val personOppgave = personOppgaveList.first()
                    personOppgave.uuid.shouldNotBeNull()
                    personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPS.uuid
                    personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    personOppgave.virksomhetsnummer shouldBeEqualTo ""
                    personOppgave.type shouldBeEqualTo personOppgaveType.name
                    personOppgave.behandletTidspunkt.shouldBeNull()
                    personOppgave.behandletVeilederIdent.shouldBeNull()
                    personOppgave.opprettet.shouldNotBeNull()
                }
            }
        }

        describe("Process OppfolgingsplanLPS-PersonOppgave for PersonIdent") {
            it("returns OK and does NOT send Personoppgavehendelse if processed 1 of 2 existing oppfolgingsplan-oppgave") {
                val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS
                val kOppfolgingsplanLPS2 = generateKOppfolgingsplanLPS2
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                val uuid = database.connection.use { connection ->
                    connection.createPersonOppgave(
                        kOppfolgingsplanLPS2,
                        personOppgaveType
                    )
                    connection.createPersonOppgave(
                        kOppfolgingsplanLPS,
                        personOppgaveType
                    ).also {
                        connection.commit()
                    }
                }

                val urlProcess = "$baseUrl/$uuid/behandle"
                val urlGet = "$baseUrl/personident"

                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val response = client.get(urlGet) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val personOppgaveList = response.body<List<PersonOppgaveVeileder>>()

                    personOppgaveList.size shouldBeEqualTo 2

                    val personOppgaveBehandletList = personOppgaveList.filter {
                        it.behandletTidspunkt != null
                    }
                    personOppgaveBehandletList.size shouldBeEqualTo 1
                    val personOppgaveBehandlet = personOppgaveBehandletList.first()
                    personOppgaveBehandlet.uuid.shouldNotBeNull()
                    personOppgaveBehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPS.uuid
                    personOppgaveBehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    personOppgaveBehandlet.virksomhetsnummer shouldBeEqualTo ""
                    personOppgaveBehandlet.type shouldBeEqualTo personOppgaveType.name
                    personOppgaveBehandlet.behandletTidspunkt.shouldNotBeNull()
                    personOppgaveBehandlet.behandletVeilederIdent.shouldNotBeNull()
                    personOppgaveBehandlet.opprettet.shouldNotBeNull()

                    val personOppgaveUbehandletList = personOppgaveList.filter {
                        it.behandletTidspunkt == null
                    }
                    personOppgaveUbehandletList.size shouldBeEqualTo 1
                    val personOppgaveUbehandlet = personOppgaveUbehandletList.first()
                    personOppgaveUbehandlet.uuid.shouldNotBeNull()
                    personOppgaveUbehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPS2.uuid
                    personOppgaveUbehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPS2.fodselsnummer
                    personOppgaveUbehandlet.virksomhetsnummer shouldBeEqualTo ""
                    personOppgaveUbehandlet.type shouldBeEqualTo personOppgaveType.name
                    personOppgaveUbehandlet.behandletTidspunkt.shouldBeNull()
                    personOppgaveUbehandlet.behandletVeilederIdent.shouldBeNull()
                    personOppgaveUbehandlet.opprettet.shouldNotBeNull()

                    verify(exactly = 0) { kafkaProducer.send(any()) }
                }
            }

            it("returns OK and sends Personoppgavehendelse if processed the 1 and only existing oppfolgingsplan-oppgave") {
                val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS
                val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                val uuid = database.connection.use { connection ->
                    connection.createPersonOppgave(
                        kOppfolgingsplanLPS,
                        personOppgaveType
                    ).also { connection.commit() }
                }

                val urlProcess = "$baseUrl/$uuid/behandle"
                val urlGet = "$baseUrl/personident"

                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val response = client.get(urlGet) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val personOppgaveList = response.body<List<PersonOppgaveVeileder>>()

                    personOppgaveList.size shouldBeEqualTo 1

                    val personOppgave = personOppgaveList.first()
                    personOppgave.uuid.shouldNotBeNull()
                    personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPS.uuid
                    personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    personOppgave.virksomhetsnummer shouldBeEqualTo ""
                    personOppgave.type shouldBeEqualTo personOppgaveType.name
                    personOppgave.behandletTidspunkt.shouldNotBeNull()
                    personOppgave.behandletVeilederIdent.shouldNotBeNull()
                    personOppgave.opprettet.shouldNotBeNull()
                }

                val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
                producedPersonoppgaveHendelse.personident shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET.name
            }

            it("returns OK on behandle dialogmotesvar") {
                val moteUuid = UUID.randomUUID()
                val dialogmotesvar = generateDialogmotesvar(moteUuid, DialogmoteSvartype.NYTT_TID_STED)

                val oppgaveUuid = database.connection.use { connection ->
                    connection.createPersonOppgave(dialogmotesvar).also { connection.commit() }
                }

                val urlProcess = "$baseUrl/$oppgaveUuid/behandle"

                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }
                }
            }
        }

        describe("Behandle behandlerdialog-oppgaver") {
            it("returns OK on behandle and sends Personoppgavehendelse if no other ubehandlede ubesvart-oppgave") {
                val meldingUuid = UUID.randomUUID()
                val ubesvartMelding = generateKMeldingDTO(uuid = meldingUuid).toMelding()
                var oppgaveUuid: UUID
                database.connection.use { connection ->
                    oppgaveUuid = connection.createPersonOppgave(
                        melding = ubesvartMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    )
                    connection.commit()
                }

                val urlProcess = "$baseUrl/$oppgaveUuid/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                    val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
                    producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET.name
                }
            }

            it("returns OK on behandle and do NOT send Personoppgavehendelse when there are other ubehandlede ubesvart-oppgaver") {
                val meldingUuid = UUID.randomUUID()
                val otherMeldingUuid = UUID.randomUUID()
                val ubesvartMelding = generateKMeldingDTO(uuid = meldingUuid).toMelding()
                val otherUbesvartMelding = generateKMeldingDTO(uuid = otherMeldingUuid).toMelding()
                var oppgaveUuid: UUID
                database.connection.use { connection ->
                    oppgaveUuid = connection.createPersonOppgave(
                        melding = ubesvartMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    )
                    connection.createPersonOppgave(
                        melding = otherUbesvartMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART,
                    )
                    connection.commit()
                }

                val urlProcess = "$baseUrl/$oppgaveUuid/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    verify(exactly = 0) { kafkaProducer.send(any()) }
                }
            }

            it("returns OK on behandle and sends Personoppgavehendelse if no other ubehandlede avvist-oppgaver") {
                val meldingUuid = UUID.randomUUID()
                val avvistMelding = generateKMeldingDTO(uuid = meldingUuid).toMelding()
                var oppgaveUuid: UUID
                database.connection.use { connection ->
                    oppgaveUuid = connection.createPersonOppgave(
                        melding = avvistMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST,
                    )
                    connection.commit()
                }

                val urlProcess = "$baseUrl/$oppgaveUuid/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                    val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
                    producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_BEHANDLET.name
                }
            }

            it("returns OK on behandle and do NOT send Personoppgavehendelse where there are other ubehandlede avvist-oppgaver") {
                val meldingUuid = UUID.randomUUID()
                val avvistMelding = generateKMeldingDTO(uuid = meldingUuid).toMelding()
                val otherAvvistMelding = generateKMeldingDTO().toMelding()
                var oppgaveUuid: UUID
                database.connection.use { connection ->
                    oppgaveUuid = connection.createPersonOppgave(
                        melding = avvistMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST,
                    )
                    connection.createPersonOppgave(
                        melding = otherAvvistMelding,
                        personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST,
                    )
                    connection.commit()
                }

                val urlProcess = "$baseUrl/$oppgaveUuid/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    verify(exactly = 0) { kafkaProducer.send(any()) }
                }
            }
        }

        describe("Behandle behandler_ber_om_bistand-oppgave") {
            it("returns OK on behandle and sends Personoppgavehendelse if no other behandler_ber_om_bistand-oppgave") {
                val sykmeldingId = UUID.randomUUID()
                val personOppgave = PersonOppgave(
                    referanseUuid = sykmeldingId,
                    personIdent = ARBEIDSTAKER_FNR,
                    type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                )
                personOppgaveRepository.createPersonoppgave(
                    personOppgave
                )

                val urlProcess = "$baseUrl/${personOppgave.uuid}/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                    verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                    val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
                    producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_BEHANDLET.name
                }
            }

            it("returns OK on behandle and do NOT send Personoppgavehendelse when there are other ubehandlede behandler_ber_om_bistand-oppgaver") {
                val sykmeldingId = UUID.randomUUID()
                val otherSykmeldingId = UUID.randomUUID()
                val oppgave = PersonOppgave(
                    referanseUuid = sykmeldingId,
                    personIdent = ARBEIDSTAKER_FNR,
                    type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                )
                val otherOppgave = PersonOppgave(
                    referanseUuid = otherSykmeldingId,
                    personIdent = ARBEIDSTAKER_FNR,
                    type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
                )
                database.connection.use { connection ->
                    personOppgaveRepository.createPersonoppgave(
                        oppgave,
                        connection,
                    )
                    personOppgaveRepository.createPersonoppgave(
                        otherOppgave,
                        connection,
                    )
                    connection.commit()
                }

                val urlProcess = "$baseUrl/${oppgave.uuid}/behandle"
                testApplication {
                    val client = setupApiAndClient()

                    client.post(urlProcess) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.OK
                    }

                    verify(exactly = 0) { kafkaProducer.send(any()) }
                }
            }
        }

        describe("Process several personoppgaver") {
            val url = "$baseUrl/behandle"
            val personoppgaveBehandlerdialog = generatePersonoppgave(
                type = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
            )
            val requestDTO = BehandlePersonoppgaveRequestDTO(
                personIdent = personoppgaveBehandlerdialog.personIdent.value,
                personOppgaveType = personoppgaveBehandlerdialog.type,
            )
            describe("Happy path") {
                it("Will behandle several personoppgaver and produce Personoppgavehendelse") {
                    database.connection.use {
                        it.createPersonOppgave(personoppgaveBehandlerdialog)
                        it.createPersonOppgave(
                            personoppgaveBehandlerdialog.copy(
                                uuid = UUID.randomUUID(),
                                referanseUuid = UUID.randomUUID()
                            )
                        )
                        it.commit()
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val personoppgaver = database.getPersonOppgaver(
                            personIdent = PersonIdent(requestDTO.personIdent),
                        )
                        personoppgaver.size shouldBeEqualTo 2
                        personoppgaver.all {
                            it.behandletVeilederIdent == VEILEDER_IDENT && it.behandletTidspunkt != null
                        } shouldBeEqualTo true
                        personoppgaver.all { !it.publish } shouldBeEqualTo true

                        val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
                        verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }

                        val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
                        producedPersonoppgaveHendelse.hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_BEHANDLET.name
                    }
                }

                it("Will only behandle personoppgaver with correct type") {
                    val personoppgaveDialogmotesvar = generatePersonoppgave()
                    database.connection.use {
                        it.createPersonOppgave(personoppgaveBehandlerdialog)
                        it.createPersonOppgave(
                            personoppgaveBehandlerdialog.copy(
                                uuid = UUID.randomUUID(),
                                referanseUuid = UUID.randomUUID()
                            )
                        )
                        it.createPersonOppgave(personoppgaveDialogmotesvar)
                        it.commit()
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val personoppgaver = database.getPersonOppgaver(
                            personIdent = PersonIdent(requestDTO.personIdent),
                        )
                        val personoppgaverBehandlerdialog = personoppgaver.filter {
                            it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR.name
                        }
                        personoppgaver.size shouldBeEqualTo 3
                        personoppgaverBehandlerdialog.size shouldBeEqualTo 2
                        personoppgaverBehandlerdialog.all {
                            it.behandletVeilederIdent == VEILEDER_IDENT && it.behandletTidspunkt != null
                        } shouldBeEqualTo true
                        personoppgaver.first {
                            it.type != PersonOppgaveType.BEHANDLERDIALOG_SVAR.name
                        }.behandletVeilederIdent shouldBeEqualTo null
                    }
                }

                it("Will only behandle ubehandlede personoppgaver") {
                    val alreadyBehandletPersonOppgave = personoppgaveBehandlerdialog.copy(
                        uuid = UUID.randomUUID(),
                        referanseUuid = UUID.randomUUID(),
                        behandletVeilederIdent = VEILEDER_IDENT,
                        behandletTidspunkt = LocalDateTime.now(),
                        publish = true,
                    )
                    database.connection.use {
                        it.createPersonOppgave(personoppgaveBehandlerdialog)
                        it.createPersonOppgave(alreadyBehandletPersonOppgave)
                        it.updatePersonOppgaveBehandlet(alreadyBehandletPersonOppgave)
                        it.commit()
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK

                        val personoppgaver = database.getPersonOppgaver(
                            personIdent = PersonIdent(requestDTO.personIdent),
                        )
                        val alreadyBehandletPersonoppgaveTidspunkt = personoppgaver.first {
                            it.uuid == alreadyBehandletPersonOppgave.uuid
                        }.behandletTidspunkt
                        val newlyBehandletPersonoppgaveTidspunkt = personoppgaver.first {
                            it.uuid == personoppgaveBehandlerdialog.uuid
                        }.behandletTidspunkt

                        personoppgaver.size shouldBeEqualTo 2
                        alreadyBehandletPersonoppgaveTidspunkt!! shouldBeBefore newlyBehandletPersonoppgaveTidspunkt!!
                    }
                }
            }

            describe("Unhappy path") {
                it("Will not behandle when no ubehandlede personoppgaver for person") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(requestDTO)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Conflict
                    }
                }
            }
        }
    }
})
