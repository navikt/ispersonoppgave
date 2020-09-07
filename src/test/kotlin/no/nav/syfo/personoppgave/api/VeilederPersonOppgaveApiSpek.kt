package no.nav.syfo.personoppgave.api

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.InternalAPI
import io.mockk.every
import io.mockk.mockkStatic
import no.nav.syfo.auth.getTokenFromCookie
import no.nav.syfo.auth.isInvalidToken
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.personoppgave.PersonOppgaveService
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.generator.veilederTokenGenerator
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket

private val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@InternalAPI
object VeilederPersonOppgaveApiSpek : Spek({

    describe("VeilederPersonOppgaveApi") {

        with(TestApplicationEngine()) {
            start()

            val responseAccessPerson = Tilgang(
                true,
                ""
            )
            val responseNoAccessPerson = Tilgang(
                false,
                ""
            )

            val mockHttpServerPort = ServerSocket(0).use { it.localPort }
            val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
            val mockServer = embeddedServer(Netty, mockHttpServerPort) {
                install(ContentNegotiation) {
                    jackson {}
                }
                routing {
                    get("/syfo-tilgangskontroll/api/tilgang/bruker") {
                        if (call.parameters["fnr"] == ARBEIDSTAKER_FNR.value) {
                            call.respond(responseAccessPerson)
                        } else {
                            call.respond(responseNoAccessPerson)
                        }
                    }
                }
            }.start()

            val database = TestDB()
            val cookies = ""
            val baseUrl = "/api/v1/personoppgave"
            val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
                mockHttpServerUrl
            )
            val personOppgaveService = PersonOppgaveService(database)

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            application.routing {
                registerVeilederPersonOppgaveApi(
                    personOppgaveService,
                    veilederTilgangskontrollClient
                )
            }

            beforeEachTest {
                mockkStatic("no.nav.syfo.auth.TokenAuthKt")
            }

            afterEachTest {
                database.connection.dropData()
            }

            afterGroup {
                mockServer.stop(1L, 10L)
                database.stop()
            }

            describe("Get PersonOppgave for PersonIdent") {
                val url = "$baseUrl/personident"

                it("should return status BadRequest if not NAV_PERSONIDENT_HEADER is supplied") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    with(handleRequest(HttpMethod.Get, url) {
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.BadRequest
                    }
                }

                it("should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid Fodselsnummer is supplied") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    with(handleRequest(HttpMethod.Get, url) {
                        addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.BadRequest
                    }
                }

                it("should return status Forbidden Veileder does not have access to request PersonIdent") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    with(handleRequest(HttpMethod.Get, url) {
                        addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1).plus("0"))
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.Forbidden
                    }
                }

                it("should return status NoContent if there is no PersonOppgaver for PersonIdent") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    with(handleRequest(HttpMethod.Get, url) {
                        addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.NoContent
                    }
                }

                it("should return PersonOppgaveList if there is a PersonOppgave for PersonIdent with type OppfolgingsplanLPS") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
                    database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    )

                    with(handleRequest(HttpMethod.Get, url) {
                        addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldEqual 1

                        val personOppgave = personOppgaveList.first()
                        personOppgave.uuid.shouldNotBeNull()
                        personOppgave.referanseUuid shouldEqual kOppfolgingsplanLPSNAV.getUuid()
                        personOppgave.fnr shouldEqual kOppfolgingsplanLPSNAV.getFodselsnummer()
                        personOppgave.virksomhetsnummer shouldEqual kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                        personOppgave.type shouldEqual personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldBeNull()
                        personOppgave.behandletVeilederIdent.shouldBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }
                }
            }

            describe("Process PersonOppgave for PersonIdent") {
                it("should return OK if Veileder processed existing PersonOppgave") {
                    every {
                        isInvalidToken(any())
                    } returns false

                    val token = veilederTokenGenerator
                    every {
                        getTokenFromCookie(any())
                    } returns token

                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV
                    val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS

                    val uuid = database.connection.createPersonOppgave(
                        kOppfolgingsplanLPSNAV,
                        personOppgaveType
                    ).second

                    val urlProcess = "$baseUrl/$uuid/behandle"
                    val urlGet = "$baseUrl/personident"

                    with(handleRequest(HttpMethod.Post, urlProcess) {
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.OK
                    }

                    with(handleRequest(HttpMethod.Get, urlGet) {
                        addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
                        call.request.cookies[cookies]
                    }) {
                        response.status() shouldEqual HttpStatusCode.OK

                        val personOppgaveList = objectMapper.readValue<List<PersonOppgaveVeileder>>(response.content!!)

                        personOppgaveList.size shouldEqual 1

                        val personOppgave = personOppgaveList.first()
                        personOppgave.uuid.shouldNotBeNull()
                        personOppgave.referanseUuid shouldEqual kOppfolgingsplanLPSNAV.getUuid()
                        personOppgave.fnr shouldEqual kOppfolgingsplanLPSNAV.getFodselsnummer()
                        personOppgave.virksomhetsnummer shouldEqual kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                        personOppgave.type shouldEqual personOppgaveType.name
                        personOppgave.behandletTidspunkt.shouldNotBeNull()
                        personOppgave.behandletVeilederIdent.shouldNotBeNull()
                        personOppgave.opprettet.shouldNotBeNull()
                    }
                }
            }
        }
    }
})
