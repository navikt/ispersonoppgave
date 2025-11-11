package no.nav.syfo.oppfolgingsplanlps

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPS
import no.nav.syfo.testutil.generators.generateKOppfolgingsplanLPSNoBehovforForBistand
import no.nav.syfo.testutil.getPersonOppgaveList
import no.nav.syfo.testutil.stopExternalMocks
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import java.util.*
import java.util.concurrent.Future

class OppfolgingsplanLPSServiceTest {
    private lateinit var kafkaProducer: KafkaProducer<String, KPersonoppgavehendelse>
    private lateinit var personoppgavehendelseProducer: PersonoppgavehendelseProducer
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var oppfolgingsplanLPSService: OppfolgingsplanLPSService

    @BeforeEach
    fun setup() {
        kafkaProducer = mockk(relaxed = true)
        personoppgavehendelseProducer = PersonoppgavehendelseProducer(kafkaProducer)
        database = externalMockEnvironment.database
        oppfolgingsplanLPSService = OppfolgingsplanLPSService(database, personoppgavehendelseProducer)
        clearMocks(kafkaProducer)
        coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        database.dropData()
    }

    @Test
    fun `should create a new PPersonOppgave with correct type when behovForBistand=true`() = runBlocking {
        val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS

        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)

        val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)
        Assertions.assertEquals(1, personListe.size)
        Assertions.assertEquals(kOppfolgingsplanLPS.fodselsnummer, personListe[0].fnr)
        Assertions.assertEquals(kOppfolgingsplanLPS.virksomhetsnummer, personListe[0].virksomhetsnummer)
        Assertions.assertEquals(PersonOppgaveType.OPPFOLGINGSPLANLPS.name, personListe[0].type)
        Assertions.assertEquals(UUID.fromString(kOppfolgingsplanLPS.uuid), personListe[0].referanseUuid)
        Assertions.assertNotNull(personListe[0].oversikthendelseTidspunkt)

        val producerRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(producerRecordSlot)) }
        val producedPersonoppgaveHendelse = producerRecordSlot.captured.value()
        Assertions.assertEquals(kOppfolgingsplanLPS.fodselsnummer, producedPersonoppgaveHendelse.personident)
        Assertions.assertEquals(PersonoppgavehendelseType.OPPFOLGINGSPLANLPS_BISTAND_MOTTATT.name, producedPersonoppgaveHendelse.hendelsetype)
    }

    @Test
    fun `should not create a new PPersonOppgave when behovForBistand=false`() = runBlocking {
        val kOppfolgingsplanLPS = generateKOppfolgingsplanLPSNoBehovforForBistand

        oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPS)

        val personListe = database.getPersonOppgaveList(ARBEIDSTAKER_FNR)
        Assertions.assertEquals(0, personListe.size)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    companion object {
        private val externalMockEnvironment: ExternalMockEnvironment = ExternalMockEnvironment()

        @AfterAll
        @JvmStatic
        fun afterAll() {
            externalMockEnvironment.stopExternalMocks()
        }
    }
}
