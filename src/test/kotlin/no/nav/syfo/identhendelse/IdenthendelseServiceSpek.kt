package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmotestatusendring.createDialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.getDialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.createDialogmotesvar
import no.nav.syfo.dialogmotesvar.getDialogmotesvar
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaver
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.generators.*
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment()
        val database = externalMockEnvironment.database
        val mockHttpClient = externalMockEnvironment.mockHttpClient
        val azureAdClient = AzureAdClient(
            azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
            azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
            azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
            httpClient = mockHttpClient,
        )
        val pdlClient = PdlClient(
            azureAdClient = azureAdClient,
            pdlClientId = externalMockEnvironment.environment.pdlClientId,
            pdlUrl = externalMockEnvironment.environment.pdlUrl,
            httpClient = mockHttpClient,
        )

        val identhendelseService = IdenthendelseService(
            database = database,
            pdlClient = pdlClient,
        )

        afterEachTest {
            database.dropData()
        }

        describe("Happy path") {
            it("Skal oppdatere personoppgave når person har fått ny ident") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val personOppgaveNewIdent = generatePersonoppgave()
                val personOppgaveOldIdent = generatePersonoppgave().copy(
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR
                )
                val personOppgaveOtherIdent = generatePersonoppgave().copy(
                    personIdent = UserConstants.ARBEIDSTAKER_3_FNR
                )
                database.connection.use {
                    it.createPersonOppgave(personOppgaveOldIdent)
                    it.createPersonOppgave(personOppgaveNewIdent)
                    it.createPersonOppgave(personOppgaveOtherIdent)
                    it.commit()
                }

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val allPersonoppgaver = database.getAllPersonoppgaver()
                allPersonoppgaver.size shouldBeEqualTo 3
                allPersonoppgaver.filter { it.fnr == personOppgaveOldIdent.personIdent.value }.size shouldBeEqualTo 0
                allPersonoppgaver.filter { it.fnr == personOppgaveNewIdent.personIdent.value }.size shouldBeEqualTo 2
            }

            it("Skal oppdatere gamle identer i dialogmøtesvar når person har fått ny ident") {
                val kafkaIdenthendelseDTO =
                    generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val dialogmotesvarNewIdent = generateDialogmotesvar()
                val dialogmotesvarOldIdent = generateDialogmotesvar().copy(
                    arbeidstakerIdent = UserConstants.ARBEIDSTAKER_2_FNR
                )
                val dialogmotesvarOtherIdent = generateDialogmotesvar().copy(
                    arbeidstakerIdent = UserConstants.ARBEIDSTAKER_3_FNR
                )
                database.connection.use {
                    it.createDialogmotesvar(dialogmotesvarOldIdent)
                    it.createDialogmotesvar(dialogmotesvarNewIdent)
                    it.createDialogmotesvar(dialogmotesvarOtherIdent)
                    it.commit()
                }
                val oldDialogmotesvar = database.connection.use {
                    it.getDialogmotesvar(dialogmotesvarOldIdent.moteuuid)
                }
                val oldIdentUpdatedAt = oldDialogmotesvar.first().updatedAt

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val allMotesvar = database.getAllMotesvar()
                allMotesvar.size shouldBeEqualTo 3
                allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarOldIdent.arbeidstakerIdent.value }.size shouldBeEqualTo 0
                allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarNewIdent.arbeidstakerIdent.value }.size shouldBeEqualTo 2
                allMotesvar.first().updatedAt shouldBeGreaterThan oldIdentUpdatedAt
            }

            it("Skal oppdatere statusendring når person har fått ny ident") {
                val kafkaIdenthendelseDTO =
                    generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val dialogmotestatusendringNewIdent = generateDialogmotestatusendring()
                val dialogmotestatusendringOldIdent = generateDialogmotestatusendring().copy(
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR
                )
                val dialogmotestatusendringOtherIdent = generateDialogmotestatusendring().copy(
                    personIdent = UserConstants.ARBEIDSTAKER_3_FNR
                )
                database.connection.use {
                    it.createDialogmoteStatusendring(dialogmotestatusendringOldIdent)
                    it.createDialogmoteStatusendring(dialogmotestatusendringNewIdent)
                    it.createDialogmoteStatusendring(dialogmotestatusendringOtherIdent)
                    it.commit()
                }

                val oldDialogmoteStatusendring = database.connection.getDialogmoteStatusendring(dialogmotestatusendringOldIdent.dialogmoteUuid)
                val oldIdentUpdatedAt = oldDialogmoteStatusendring.first().updatedAt

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val allDialogmotestatusendring = database.getAllDialogmoteStatusendring()
                allDialogmotestatusendring.size shouldBeEqualTo 3
                allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringOldIdent.personIdent.value }.size shouldBeEqualTo 0
                allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringNewIdent.personIdent.value }.size shouldBeEqualTo 2
                allDialogmotestatusendring.first().updatedAt shouldBeGreaterThan oldIdentUpdatedAt
            }
        }

        describe("Unhappy path") {
            it("Skal kaste feil hvis PDL ikke har oppdatert identen") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                    personident = UserConstants.ARBEIDSTAKER_3_FNR,
                    hasOldPersonident = true,
                )
                val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                val kOppfolgingsplanLPS = generateKOppfolgingsplanLPS.copy(fodselsnummer = oldIdent.value)
                database.createPersonOppgave(
                    kOppfolgingsplanLPS = kOppfolgingsplanLPS,
                    type = PersonOppgaveType.OPPFOLGINGSPLANLPS,
                )

                val currentPersonOppgave = database.getPersonOppgaver(oldIdent)
                currentPersonOppgave.size shouldBeEqualTo 1

                runBlocking {
                    assertFailsWith(IllegalStateException::class) {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }
                }
            }
        }
    }
})
