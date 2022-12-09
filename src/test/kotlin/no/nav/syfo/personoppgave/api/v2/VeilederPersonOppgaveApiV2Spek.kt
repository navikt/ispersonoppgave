package no.nav.syfo.personoppgave.api.v2

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.personoppgavehendelse.domain.*
import no.nav.syfo.personoppgave.api.PersonOppgaveVeileder
import no.nav.syfo.personoppgave.createPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.UserConstants.VEILEDER_IDENT
import no.nav.syfo.util.*
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList

class VeilederPersonOppgaveApiV2Spek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

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

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                personoppgavehendelseProducer = personoppgavehendelseProducer,
            )

            afterEachTest {
                database.connection.dropData()
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
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPS
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                    database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    )

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
                        personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.uuid
                        personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.fodselsnummer
                        personOppgave.virksomhetsnummer shouldBeEqualTo ""
                        personOppgave.type shouldBeEqualTo personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldBeNull()
                        personOppgave.behandletVeilederIdent.shouldBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }
                }
            }

            describe("Process PersonOppgave for PersonIdent") {
                it("returns OK and does NOT send Personoppgavehendelse if processed 1 of 2 existing PersonOppgave") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPS
                    val kOppfolgingsplanLPSNAV2 = generateKOppfolgingsplanLPS2
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                    val uuid = database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    ).second

                    database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV2,
                        personOppgaveType
                    ).second

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
                        personOppgaveBehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.uuid
                        personOppgaveBehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.fodselsnummer
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
                        personOppgaveUbehandlet.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV2.uuid
                        personOppgaveUbehandlet.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV2.fodselsnummer
                        personOppgaveUbehandlet.virksomhetsnummer shouldBeEqualTo ""
                        personOppgaveUbehandlet.type shouldBeEqualTo personOppgaveType.name
                        personOppgaveUbehandlet.behandletTidspunkt.shouldBeNull()
                        personOppgaveUbehandlet.behandletVeilederIdent.shouldBeNull()
                        personOppgaveUbehandlet.opprettet.shouldNotBeNull()

                        val messages: ArrayList<KPersonoppgavehendelse> = arrayListOf()
                        consumerPersonoppgavehendelse.poll(Duration.ofMillis(5000)).forEach {
                            val consumedPersonoppgavehendelse: KPersonoppgavehendelse = objectMapper.readValue(it.value())
                            messages.add(consumedPersonoppgavehendelse)
                        }
                        messages.size shouldBeEqualTo 0
                    }
                }

                it("returns OK and sends Personoppgavehendelse if processed the 1 and only existing PersonOppgave") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPS
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                    val uuid = database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    ).second

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
                        personOppgave.referanseUuid shouldBeEqualTo kOppfolgingsplanLPSNAV.uuid
                        personOppgave.fnr shouldBeEqualTo kOppfolgingsplanLPSNAV.fodselsnummer
                        personOppgave.virksomhetsnummer shouldBeEqualTo ""
                        personOppgave.type shouldBeEqualTo personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldNotBeNull()
                        personOppgave.behandletVeilederIdent.shouldNotBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }

                    val messages: ArrayList<KPersonoppgavehendelse> = arrayListOf()
                    consumerPersonoppgavehendelse.poll(Duration.ofMillis(5000)).forEach {
                        val consumedPersonoppgavehendelse: KPersonoppgavehendelse = objectMapper.readValue(it.value())
                        messages.add(consumedPersonoppgavehendelse)
                    }
                    messages.size shouldBeEqualTo 1
                    messages.first().personident shouldBeEqualTo kOppfolgingsplanLPSNAV.fodselsnummer
                    messages.first().hendelsetype shouldBeEqualTo PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET.name
                }

                it("returns OK on behandle dialogmotesvar") {
                    val moteUuid = UUID.randomUUID()
                    val oppgaveUuid = UUID.randomUUID()
                    val dialogmotesvar = generateDialogmotesvar(moteUuid, DialogmoteSvartype.NYTT_TID_STED)

                    database.connection.use { connection ->
                        connection.createPersonOppgave(dialogmotesvar, oppgaveUuid)
                        connection.commit()
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
        }
    }
})
