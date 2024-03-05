package no.nav.syfo.personoppgave.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.database.PersonOppgaveRepository
import no.nav.syfo.personoppgavehendelse.domain.*
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.personoppgave.updatePersonOppgaveBehandlet
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VEILEDER_IDENT
import no.nav.syfo.testutil.generators.*
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

val objectMapper: ObjectMapper = configuredJacksonMapper()

class VeilederPersonOppgaveApiV2Spek : Spek({

    describe(VeilederPersonOppgaveApiV2Spek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database

            val baseUrl = registerVeilederPersonOppgaveApiV2BasePath

            val consumerPersonoppgavehendelse = testPersonoppgavehendelseConsumer(
                environment = externalMockEnvironment.environment,
            )
            val personoppgavehendelseProducer = testPersonoppgavehendelseProducer(
                environment = externalMockEnvironment.environment,
            )
            val personOppgaveRepository = PersonOppgaveRepository(database = database)

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )

            afterEachTest {
                database.dropData()
            }

            beforeGroup {
                externalMockEnvironment.startExternalMocks()
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

                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("returns status BadRequest if NAV_PERSONIDENT_HEADER has an invalid Fodselsnummer") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("returns status Forbidden if Veileder does not have access to request PersonIdent") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1).plus("0"))
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }

                it("returns status NoContent if there is no PersonOppgaver for PersonIdent") {
                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.NoContent
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

                    with(
                        handleRequest(HttpMethod.Get, url) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldBeEqualTo 1

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

                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlGet) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

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

                        val messages = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        messages.size shouldBeEqualTo 0
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

                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
                    }

                    with(
                        handleRequest(HttpMethod.Get, urlGet) {
                            addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

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

                    val messages = getRecordsFromTopic(consumerPersonoppgavehendelse)
                    messages.size shouldBeEqualTo 1
                    messages.first().personident shouldBeEqualTo kOppfolgingsplanLPS.fodselsnummer
                    messages.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET.name
                }

                it("returns OK on behandle dialogmotesvar") {
                    val moteUuid = UUID.randomUUID()
                    val dialogmotesvar = generateDialogmotesvar(moteUuid, DialogmoteSvartype.NYTT_TID_STED)

                    val oppgaveUuid = database.connection.use { connection ->
                        connection.createPersonOppgave(dialogmotesvar).also { connection.commit() }
                    }

                    val urlProcess = "$baseUrl/$oppgaveUuid/behandle"
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 1
                        records.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET.name
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 0
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 1
                        records.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_BEHANDLET.name
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 0
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 1
                        records.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_BEHANDLET.name
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
                    with(
                        handleRequest(HttpMethod.Post, urlProcess) {
                            addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                        }
                    ) {
                        response.status() shouldBeEqualTo HttpStatusCode.OK

                        val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                        records.size shouldBeEqualTo 0
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

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val personoppgaver = database.getPersonOppgaver(
                                personIdent = PersonIdent(requestDTO.personIdent),
                            )
                            personoppgaver.size shouldBeEqualTo 2
                            personoppgaver.all {
                                it.behandletVeilederIdent == VEILEDER_IDENT && it.behandletTidspunkt != null
                            } shouldBeEqualTo true
                            personoppgaver.all { !it.publish } shouldBeEqualTo true

                            val records = getRecordsFromTopic(consumerPersonoppgavehendelse)
                            records.size shouldBeEqualTo 1
                            records.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_BEHANDLET.name
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

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

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

                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

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
                        with(
                            handleRequest(HttpMethod.Post, url) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                setBody(objectMapper.writeValueAsString(requestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Conflict
                        }
                    }
                }
            }
        }
    }
})

fun getRecordsFromTopic(consumer: KafkaConsumer<String, String>): MutableList<KPersonoppgavehendelse> {
    val records: MutableList<KPersonoppgavehendelse> = mutableListOf()
    consumer.poll(Duration.ofMillis(5000)).forEach {
        val consumedPersonoppgavehendelse: KPersonoppgavehendelse = objectMapper.readValue(it.value())
        records.add(consumedPersonoppgavehendelse)
    }
    return records
}
