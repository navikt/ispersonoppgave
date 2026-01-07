package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.database.queries.createDialogmoteStatusendring
import no.nav.syfo.infrastructure.database.queries.getDialogmoteStatusendring
import no.nav.syfo.infrastructure.database.queries.createDialogmotesvar
import no.nav.syfo.infrastructure.database.queries.getDialogmotesvar
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.database.queries.getPersonOppgaver
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.generators.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.*

class IdenthendelseServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val mockHttpClient = externalMockEnvironment.mockHttpClient
    private val azureAdClient = AzureAdClient(
        azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
        azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
        azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
        httpClient = mockHttpClient,
    )
    private val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlClientId = externalMockEnvironment.environment.pdlClientId,
        pdlUrl = externalMockEnvironment.environment.pdlUrl,
        httpClient = mockHttpClient,
    )
    private val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    @Test
    fun `Skal oppdatere personoppgave når person har fått ny ident`() = runBlocking {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val personOppgaveNewIdent = generatePersonoppgave()
        val personOppgaveOldIdent = generatePersonoppgave().copy(personIdent = UserConstants.ARBEIDSTAKER_2_FNR)
        val personOppgaveOtherIdent = generatePersonoppgave().copy(personIdent = UserConstants.ARBEIDSTAKER_3_FNR)
        database.connection.use {
            it.createPersonOppgave(personOppgaveOldIdent)
            it.createPersonOppgave(personOppgaveNewIdent)
            it.createPersonOppgave(personOppgaveOtherIdent)
            it.commit()
        }

        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

        val allPersonoppgaver = database.getAllPersonoppgaver()
        assertEquals(3, allPersonoppgaver.size)
        assertEquals(0, allPersonoppgaver.filter { it.fnr == personOppgaveOldIdent.personIdent.value }.size)
        assertEquals(2, allPersonoppgaver.filter { it.fnr == personOppgaveNewIdent.personIdent.value }.size)
    }

    @Test
    fun `Skal oppdatere gamle identer i dialogmotesvar når person har fått ny ident`() = runBlocking {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val dialogmotesvarNewIdent = generateDialogmotesvar()
        val dialogmotesvarOldIdent = generateDialogmotesvar().copy(arbeidstakerIdent = UserConstants.ARBEIDSTAKER_2_FNR)
        val dialogmotesvarOtherIdent = generateDialogmotesvar().copy(arbeidstakerIdent = UserConstants.ARBEIDSTAKER_3_FNR)
        database.connection.use {
            it.createDialogmotesvar(dialogmotesvarOldIdent)
            it.createDialogmotesvar(dialogmotesvarNewIdent)
            it.createDialogmotesvar(dialogmotesvarOtherIdent)
            it.commit()
        }
        val oldDialogmotesvar = database.connection.use { it.getDialogmotesvar(dialogmotesvarOldIdent.moteuuid) }
        val oldIdentUpdatedAt = oldDialogmotesvar.first().updatedAt

        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

        val allMotesvar = database.getAllMotesvar()
        assertEquals(3, allMotesvar.size)
        assertEquals(0, allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarOldIdent.arbeidstakerIdent.value }.size)
        assertEquals(2, allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarNewIdent.arbeidstakerIdent.value }.size)
        assertTrue(allMotesvar.first().updatedAt.isAfter(oldIdentUpdatedAt))
    }

    @Test
    fun `Skal oppdatere statusendring når person har fått ny ident`() = runBlocking {
        val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
        val dialogmotestatusendringNewIdent = generateDialogmotestatusendring()
        val dialogmotestatusendringOldIdent = generateDialogmotestatusendring().copy(personIdent = UserConstants.ARBEIDSTAKER_2_FNR)
        val dialogmotestatusendringOtherIdent = generateDialogmotestatusendring().copy(personIdent = UserConstants.ARBEIDSTAKER_3_FNR)
        database.connection.use {
            it.createDialogmoteStatusendring(dialogmotestatusendringOldIdent)
            it.createDialogmoteStatusendring(dialogmotestatusendringNewIdent)
            it.createDialogmoteStatusendring(dialogmotestatusendringOtherIdent)
            it.commit()
        }
        val oldDialogmoteStatusendring = database.connection.getDialogmoteStatusendring(dialogmotestatusendringOldIdent.dialogmoteUuid)
        val oldIdentUpdatedAt = oldDialogmoteStatusendring.first().updatedAt

        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

        val allDialogmotestatusendring = database.getAllDialogmoteStatusendring()
        assertEquals(3, allDialogmotestatusendring.size)
        assertEquals(0, allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringOldIdent.personIdent.value }.size)
        assertEquals(2, allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringNewIdent.personIdent.value }.size)
        assertTrue(allDialogmotestatusendring.first().updatedAt.isAfter(oldIdentUpdatedAt))
    }

    @Test
    fun `Skal kaste feil hvis PDL ikke har oppdatert identen`() {
        runBlocking {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                personident = UserConstants.ARBEIDSTAKER_3_FNR,
                hasOldPersonident = true,
            )
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS.copy(fodselsnummer = oldIdent.value)
            database.createPersonOppgave(kOppfolgingsplanLPS = kOppfolgingsplanLPS, type = PersonOppgaveType.OPPFOLGINGSPLANLPS)

            val currentPersonOppgave = database.getPersonOppgaver(oldIdent)
            assertEquals(1, currentPersonOppgave.size)

            assertThrows(IllegalStateException::class.java) {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }
        }
    }
}
