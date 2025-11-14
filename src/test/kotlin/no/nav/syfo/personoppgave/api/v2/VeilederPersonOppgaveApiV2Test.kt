package no.nav.syfo.personoppgave.api.v2

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.behandlerdialog.domain.toMelding
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.dialogmotesvar.domain.DialogmoteSvartype
import no.nav.syfo.personoppgave.domain.PersonIdent
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
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Future

class VeilederPersonOppgaveApiV2Test {
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var kafkaProducer: KafkaProducer<String, KPersonoppgavehendelse>
    private lateinit var personoppgavehendelseProducer: PersonoppgavehendelseProducer
    private lateinit var personOppgaveRepository: PersonOppgaveRepository
    private val baseUrl = registerVeilederPersonOppgaveApiV2BasePath
    private lateinit var validToken: String

    @BeforeEach
    fun setup() {
        database = externalMockEnvironment.database
        kafkaProducer = mockk(relaxed = true)
        personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)
        personOppgaveRepository = PersonOppgaveRepository(database = database)
        clearMocks(kafkaProducer)
        coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        validToken = generateJWT(
            audience = externalMockEnvironment.environment.azureAppClientId,
            issuer = externalMockEnvironment.wellKnownInternADV2Mock.issuer,
            navIdent = VEILEDER_IDENT,
        )
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                personoppgavehendelseProducer = personoppgavehendelseProducer
            )
        }
        return createClient {
            install(ContentNegotiation) { jackson { configure() } }
        }
    }

    @Test
    fun `returns BadRequest if NAV_PERSONIDENT_HEADER missing`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get("$baseUrl/personident") { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns BadRequest if NAV_PERSONIDENT_HEADER invalid`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns Forbidden if veileder lacks access`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value.drop(1) + "0")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `returns NoContent if no oppgaver`() = testApplication {
        val client = setupApiAndClient()
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `returns PersonOppgaveList for OppfolgingsplanLPS`() = testApplication {
        val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS
        val personOppgaveType = PersonOppgaveType.OPPFOLGINGSPLANLPS
        database.connection.use {
            it.createPersonOppgave(kOppfolgingsplanLPS, personOppgaveType)
            it.commit()
        }
        val client = setupApiAndClient()
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val list = response.body<List<PersonOppgaveVeileder>>()
        assertEquals(1, list.size)
        val personOppgave = list.first()
        assertNotNull(personOppgave.uuid)
        assertEquals(kOppfolgingsplanLPS.uuid, personOppgave.referanseUuid)
        assertEquals(kOppfolgingsplanLPS.fodselsnummer, personOppgave.fnr)
        assertEquals("", personOppgave.virksomhetsnummer)
        assertEquals(personOppgaveType.name, personOppgave.type)
        assertNull(personOppgave.behandletTidspunkt)
        assertNull(personOppgave.behandletVeilederIdent)
        assertNotNull(personOppgave.opprettet)
    }

    @Test
    fun `processing one of two oppfolgingsplan-oppgaver does not send hendelse`() = testApplication {
        val k1 = generateKOppfolgingsplanLPS
        val k2 = generateKOppfolgingsplanLPS2
        val type = PersonOppgaveType.OPPFOLGINGSPLANLPS
        val uuid = database.connection.use { c ->
            c.createPersonOppgave(k2, type)
            c.createPersonOppgave(k1, type).also { c.commit() }
        }
        val urlProcess = "$baseUrl/$uuid/behandle"
        val client = setupApiAndClient()
        client.post(urlProcess) { bearerAuth(validToken) }.apply { assertEquals(HttpStatusCode.OK, status) }
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val list = response.body<List<PersonOppgaveVeileder>>()
        assertEquals(2, list.size)
        val behandlet = list.first { it.behandletTidspunkt != null }
        assertEquals(k1.uuid, behandlet.referanseUuid)
        val ubehandlet = list.first { it.behandletTidspunkt == null }
        assertEquals(k2.uuid, ubehandlet.referanseUuid)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `processing only oppfolgingsplan-oppgave sends hendelse`() = testApplication {
        val k = generateKOppfolgingsplanLPS
        val type = PersonOppgaveType.OPPFOLGINGSPLANLPS
        val uuid = database.connection.use { c -> c.createPersonOppgave(k, type).also { c.commit() } }
        val client = setupApiAndClient()
        client.post("$baseUrl/$uuid/behandle") { bearerAuth(validToken) }.apply { assertEquals(HttpStatusCode.OK, status) }
        val response = client.get("$baseUrl/personident") {
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_FNR.value)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val list = response.body<List<PersonOppgaveVeileder>>()
        assertEquals(1, list.size)
        val oppgave = list.first()
        assertNotNull(oppgave.behandletTidspunkt)
        val slotRecord = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        val hendelse = slotRecord.captured.value()
        assertEquals(k.fodselsnummer, hendelse.personident)
        assertEquals(PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_BEHANDLET.name, hendelse.hendelsetype)
    }

    @Test
    fun `behandle dialogmotesvar returns OK`() = testApplication {
        val moteUuid = UUID.randomUUID()
        val dialogmotesvar = generateDialogmotesvar(moteUuid, DialogmoteSvartype.NYTT_TID_STED)
        val oppgaveUuid = database.connection.use { c -> c.createPersonOppgave(dialogmotesvar).also { c.commit() } }
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/$oppgaveUuid/behandle") { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `behandle ubesvart oppgave sends hendelse when no other ubehandlede`() = testApplication {
        val meldingUuid = UUID.randomUUID()
        val ubesvartMelding = generateKMeldingDTO(uuid = meldingUuid).toMelding()
        val oppgaveUuid = database.connection.use { c -> c.createPersonOppgave(ubesvartMelding, PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART).also { c.commit() } }
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/$oppgaveUuid/behandle") { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        val slotRecord = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_UBESVART_BEHANDLET.name, slotRecord.captured.value().hendelsetype)
    }

    @Test
    fun `behandle ubesvart oppgave no hendelse when other ubehandlede exist`() = testApplication {
        val m1 = generateKMeldingDTO().toMelding()
        val m2 = generateKMeldingDTO().toMelding()
        database.connection.use { c ->
            c.createPersonOppgave(m1, PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART)
            val uuid = c.createPersonOppgave(m2, PersonOppgaveType.BEHANDLERDIALOG_MELDING_UBESVART)
            c.commit()
            val client = setupApiAndClient()
            val response = client.post("$baseUrl/$uuid/behandle") { bearerAuth(validToken) }
            assertEquals(HttpStatusCode.OK, response.status)
            verify(exactly = 0) { kafkaProducer.send(any()) }
        }
    }

    @Test
    fun `behandle avvist oppgave sends hendelse when alone`() = testApplication {
        val melding = generateKMeldingDTO().toMelding()
        val oppgaveUuid = database.connection.use { c -> c.createPersonOppgave(melding, PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST).also { c.commit() } }
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/$oppgaveUuid/behandle") { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        val slotRecord = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_MELDING_AVVIST_BEHANDLET.name, slotRecord.captured.value().hendelsetype)
    }

    @Test
    fun `behandle avvist oppgave no hendelse when others exist`() = testApplication {
        database.connection.use { c ->
            val m1 = generateKMeldingDTO().toMelding()
            val m2 = generateKMeldingDTO().toMelding()
            c.createPersonOppgave(m1, PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST)
            val uuid = c.createPersonOppgave(m2, PersonOppgaveType.BEHANDLERDIALOG_MELDING_AVVIST)
            c.commit()
            val client = setupApiAndClient()
            val response = client.post("$baseUrl/$uuid/behandle") { bearerAuth(validToken) }
            assertEquals(HttpStatusCode.OK, response.status)
            verify(exactly = 0) { kafkaProducer.send(any()) }
        }
    }

    @Test
    fun `behandle behandler ber om bistand oppgave sends hendelse when alone`() = testApplication {
        val sykmeldingId = UUID.randomUUID()
        val oppgave = PersonOppgave(referanseUuid = sykmeldingId, personIdent = ARBEIDSTAKER_FNR, type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND)
        personOppgaveRepository.createPersonoppgave(oppgave)
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/${oppgave.uuid}/behandle") { bearerAuth(validToken) }
        assertEquals(HttpStatusCode.OK, response.status)
        val slotRecord = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        assertEquals(PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_BEHANDLET.name, slotRecord.captured.value().hendelsetype)
    }

    @Test
    fun `behandle behandler ber om bistand oppgave no hendelse when others exist`() = testApplication {
        val oppgave1 = PersonOppgave(referanseUuid = UUID.randomUUID(), personIdent = ARBEIDSTAKER_FNR, type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND)
        val oppgave2 = PersonOppgave(referanseUuid = UUID.randomUUID(), personIdent = ARBEIDSTAKER_FNR, type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND)
        database.connection.use { c ->
            personOppgaveRepository.createPersonoppgave(oppgave1, c)
            personOppgaveRepository.createPersonoppgave(oppgave2, c)
            c.commit()
            val client = setupApiAndClient()
            val response = client.post("$baseUrl/${oppgave1.uuid}/behandle") { bearerAuth(validToken) }
            assertEquals(HttpStatusCode.OK, response.status)
            verify(exactly = 0) { kafkaProducer.send(any()) }
        }
    }

    @Test
    fun `process several personoppgaver updates them and produces hendelse`() = testApplication {
        val p = generatePersonoppgave(type = PersonOppgaveType.BEHANDLERDIALOG_SVAR)
        val request = BehandlePersonoppgaveRequestDTO(personIdent = p.personIdent.value, personOppgaveType = p.type)
        database.connection.use { c ->
            c.createPersonOppgave(p)
            c.createPersonOppgave(p.copy(uuid = UUID.randomUUID(), referanseUuid = UUID.randomUUID()))
            c.commit()
        }
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/behandle") {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val personoppgaver = database.getPersonOppgaver(PersonIdent(request.personIdent))
        assertEquals(2, personoppgaver.size)
        assertTrue(personoppgaver.all { it.behandletVeilederIdent == VEILEDER_IDENT && it.behandletTidspunkt != null })
        assertTrue(personoppgaver.all { !it.publish })
        val slotRecord = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(slotRecord)) }
        assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_BEHANDLET.name, slotRecord.captured.value().hendelsetype)
    }

    @Test
    fun `process several personoppgaver only updates correct type`() = testApplication {
        val pSvar = generatePersonoppgave(type = PersonOppgaveType.BEHANDLERDIALOG_SVAR)
        val pOther = generatePersonoppgave()
        val request = BehandlePersonoppgaveRequestDTO(personIdent = pSvar.personIdent.value, personOppgaveType = pSvar.type)
        database.connection.use { c ->
            c.createPersonOppgave(pSvar)
            c.createPersonOppgave(pSvar.copy(uuid = UUID.randomUUID(), referanseUuid = UUID.randomUUID()))
            c.createPersonOppgave(pOther)
            c.commit()
        }
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/behandle") {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val personoppgaver = database.getPersonOppgaver(PersonIdent(request.personIdent))
        val svarOppgaver = personoppgaver.filter { it.type == PersonOppgaveType.BEHANDLERDIALOG_SVAR.name }
        assertEquals(3, personoppgaver.size)
        assertEquals(2, svarOppgaver.size)
        assertTrue(svarOppgaver.all { it.behandletVeilederIdent == VEILEDER_IDENT && it.behandletTidspunkt != null })
        assertNull(personoppgaver.first { it.type != PersonOppgaveType.BEHANDLERDIALOG_SVAR.name }.behandletVeilederIdent)
    }

    @Test
    fun `process several personoppgaver skips already behandlet`() = testApplication {
        val p = generatePersonoppgave(type = PersonOppgaveType.BEHANDLERDIALOG_SVAR)
        val already = p.copy(uuid = UUID.randomUUID(), referanseUuid = UUID.randomUUID(), behandletVeilederIdent = VEILEDER_IDENT, behandletTidspunkt = LocalDateTime.now(), publish = true)
        database.connection.use { c ->
            c.createPersonOppgave(p)
            c.createPersonOppgave(already)
            c.updatePersonOppgaveBehandlet(already)
            c.commit()
        }
        val request = BehandlePersonoppgaveRequestDTO(personIdent = p.personIdent.value, personOppgaveType = p.type)
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/behandle") {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val list = database.getPersonOppgaver(PersonIdent(request.personIdent))
        val alreadyTid = list.first { it.uuid == already.uuid }.behandletTidspunkt!!
        val newlyTid = list.first { it.uuid == p.uuid }.behandletTidspunkt!!
        assertTrue(alreadyTid.isBefore(newlyTid))
    }

    @Test
    fun `process several personoppgaver conflict when none to behandle`() = testApplication {
        val request = BehandlePersonoppgaveRequestDTO(personIdent = ARBEIDSTAKER_FNR.value, personOppgaveType = PersonOppgaveType.BEHANDLERDIALOG_SVAR)
        val client = setupApiAndClient()
        val response = client.post("$baseUrl/behandle") {
            bearerAuth(validToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    companion object {
        private val externalMockEnvironment = ExternalMockEnvironment.instance
    }
}
