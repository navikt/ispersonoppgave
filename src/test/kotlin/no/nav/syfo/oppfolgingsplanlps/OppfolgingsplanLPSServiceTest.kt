package no.nav.syfo.oppfolgingsplanlps

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingsplanLPSService
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.infrastructure.kafka.oppgavehendelse.KPersonoppgavehendelse
import no.nav.syfo.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPS
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPSNoBehovforForBistand
import no.nav.syfo.testutil.getPersonOppgaveList
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.*
import java.util.concurrent.Future

class OppfolgingsplanLPSServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val kafkaProducer: KafkaProducer<String, KPersonoppgavehendelse> = mockk(relaxed = true)
    private val personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)
    private val oppfolgingsplanLPSService = OppfolgingsplanLPSService(database, personoppgavehendelseProducer)

    @BeforeEach
    fun setup() {
        clearMocks(kafkaProducer)
        coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        database.dropData()
    }

    @Test
    fun `should create a new PPersonOppgave with correct type when behovForBistand=true`() = runBlocking {
        val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS

        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)

        val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)
        assertEquals(1, personListe.size)
        assertEquals(kOppfolgingsplanLPS.fodselsnummer, personListe[0].fnr)
        assertEquals(kOppfolgingsplanLPS.virksomhetsnummer, personListe[0].virksomhetsnummer)
        assertEquals(PersonOppgaveType.OPPFOLGINGSPLANLPS.name, personListe[0].type)
        assertEquals(UUID.fromString(kOppfolgingsplanLPS.uuid), personListe[0].referanseUuid)
        assertNotNull(personListe[0].oversikthendelseTidspunkt)

        val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }
        val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
        assertEquals(kOppfolgingsplanLPS.fodselsnummer, producedPersonoppgaveHendelse.personident)
        assertEquals(PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name, producedPersonoppgaveHendelse.hendelsetype)
    }

    @Test
    fun `should not create a new PPersonOppgave when behovForBistand=false`() = runBlocking {
        val kOppfolgingsplanLPS = generateKOppfolgingsplanLPSNoBehovforForBistand

        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)

        val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)
        assertEquals(0, personListe.size)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }
}
