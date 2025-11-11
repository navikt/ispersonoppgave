package no.nav.syfo.personoppgavehendelse.cronjob

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import no.nav.syfo.personoppgave.infrastructure.database.PersonOppgaveRepository
import no.nav.syfo.personoppgave.domain.PersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.updatePersonoppgaveSetBehandlet
import no.nav.syfo.personoppgavehendelse.PersonoppgavehendelseProducer
import no.nav.syfo.personoppgavehendelse.PublishPersonoppgavehendelseService
import no.nav.syfo.personoppgavehendelse.domain.KPersonoppgavehendelse
import no.nav.syfo.personoppgavehendelse.domain.PersonoppgavehendelseType
import no.nav.syfo.testutil.ExternalMockEnvironment
import no.nav.syfo.testutil.UserConstants
import no.nav.syfo.testutil.createPersonOppgave
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generators.generatePersonoppgave
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Future

class PublishOppgavehendelseCronjobTest {
    private lateinit var externalMockEnvironment: ExternalMockEnvironment
    private lateinit var database: no.nav.syfo.personoppgave.infrastructure.database.DatabaseInterface
    private lateinit var kafkaProducer: KafkaProducer<String, KPersonoppgavehendelse>
    private lateinit var personoppgaveHendelseProducer: PersonoppgavehendelseProducer
    private lateinit var publishOppgavehendelseService: PublishPersonoppgavehendelseService
    private lateinit var publishOppgavehendelseCronjob: PublishOppgavehendelseCronjob

    private lateinit var unpublishedUbehandletPersonoppgave: PersonOppgave

    @BeforeEach
    fun setup() {
        externalMockEnvironment = ExternalMockEnvironment()
        database = externalMockEnvironment.database
        kafkaProducer = mockk()
        personoppgaveHendelseProducer = PersonoppgavehendelseProducer(producer = kafkaProducer)
        publishOppgavehendelseService = PublishPersonoppgavehendelseService(
            database = database,
            personoppgavehendelseProducer = personoppgaveHendelseProducer,
            personOppgaveRepository = PersonOppgaveRepository(database = database),
        )
        publishOppgavehendelseCronjob = PublishOppgavehendelseCronjob(publishOppgavehendelseService = publishOppgavehendelseService)
        clearMocks(kafkaProducer)
        coEvery { kafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        database.dropData()
        unpublishedUbehandletPersonoppgave = generatePersonoppgave(
            personIdent = UserConstants.ARBEIDSTAKER_FNR,
            type = PersonOppgaveType.BEHANDLER_BER_OM_BISTAND,
            publish = true,
        )
    }

    private fun createPersonoppgaver(vararg oppgaver: PersonOppgave) {
        database.connection.use {
            oppgaver.forEach { oppgave -> it.createPersonOppgave(oppgave) }
            it.commit()
        }
    }

    @Test
    fun `Will publish unpublished personoppgaver`() = runBlocking {
        createPersonoppgaver(
            unpublishedUbehandletPersonoppgave,
            unpublishedUbehandletPersonoppgave.copy(
                uuid = UUID.randomUUID(),
                referanseUuid = UUID.randomUUID(),
                type = PersonOppgaveType.BEHANDLERDIALOG_SVAR,
            ),
        )
        val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()
        Assertions.assertEquals(0, result.failed)
        Assertions.assertEquals(2, result.updated)

        val kafkaRecordSlot1 = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        val kafkaRecordSlot2 = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verifyOrder {
            kafkaProducer.send(capture(kafkaRecordSlot1))
            kafkaProducer.send(capture(kafkaRecordSlot2))
        }
        val kafkaPersonoppgavehendelse1 = kafkaRecordSlot1.captured.value()
        Assertions.assertEquals(unpublishedUbehandletPersonoppgave.personIdent.value, kafkaPersonoppgavehendelse1.personident)
        Assertions.assertEquals(PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_MOTTATT.name, kafkaPersonoppgavehendelse1.hendelsetype)
        val kafkaPersonoppgavehendelse2 = kafkaRecordSlot2.captured.value()
        Assertions.assertEquals(unpublishedUbehandletPersonoppgave.personIdent.value, kafkaPersonoppgavehendelse2.personident)
        Assertions.assertEquals(PersonoppgavehendelseType.BEHANDLERDIALOG_SVAR_MOTTATT.name, kafkaPersonoppgavehendelse2.hendelsetype)
    }

    @Test
    fun `Will only publish newest unpublished personoppgave of same type`() = runBlocking {
        val unpublishedBehandletPersonoppgave = unpublishedUbehandletPersonoppgave.copy(
            uuid = UUID.randomUUID(),
            referanseUuid = UUID.randomUUID(),
            sistEndret = LocalDateTime.now().plusMinutes(1),
            behandletTidspunkt = LocalDateTime.now(),
            behandletVeilederIdent = UserConstants.VEILEDER_IDENT,
        )
        database.connection.use {
            it.createPersonOppgave(unpublishedUbehandletPersonoppgave)
            it.createPersonOppgave(unpublishedBehandletPersonoppgave)
            it.updatePersonoppgaveSetBehandlet(unpublishedBehandletPersonoppgave)
            it.commit()
        }

        val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()
        Assertions.assertEquals(0, result.failed)
        Assertions.assertEquals(2, result.updated) // maybe-publish increments updated regardless

        val kafkaRecordSlot = slot<ProducerRecord<String, KPersonoppgavehendelse>>()
        verify(exactly = 1) { kafkaProducer.send(capture(kafkaRecordSlot)) }
        val kafkaPersonoppgavehendelse = kafkaRecordSlot.captured.value()
        Assertions.assertEquals(unpublishedUbehandletPersonoppgave.personIdent.value, kafkaPersonoppgavehendelse.personident)
        Assertions.assertEquals(PersonoppgavehendelseType.BEHANDLER_BER_OM_BISTAND_BEHANDLET.name, kafkaPersonoppgavehendelse.hendelsetype)
    }

    @Test
    fun `Will not publish already published oppgave`() = runBlocking {
        val alreadyPublishedOppgave = unpublishedUbehandletPersonoppgave.copy(publishedAt = OffsetDateTime.now(), publish = false)
        createPersonoppgaver(alreadyPublishedOppgave)
        val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()
        Assertions.assertEquals(0, result.failed)
        Assertions.assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will not publish if no personoppgaver`() = runBlocking {
        val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()
        Assertions.assertEquals(0, result.failed)
        Assertions.assertEquals(0, result.updated)
        verify(exactly = 0) { kafkaProducer.send(any()) }
    }

    @Test
    fun `Will fail when publishing to topic throws error`() = runBlocking {
        createPersonoppgaver(unpublishedUbehandletPersonoppgave)
        coEvery { kafkaProducer.send(any()) } coAnswers { throw Exception() }
        val result = publishOppgavehendelseCronjob.publishOppgavehendelserJob()
        Assertions.assertEquals(1, result.failed)
        Assertions.assertEquals(0, result.updated)
        verify(exactly = 1) { kafkaProducer.send(any()) }
    }
}
