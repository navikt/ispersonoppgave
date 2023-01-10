package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmotestatusendring.createDialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.createDialogmotesvar
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.personoppgave.getPersonOppgaveList
import no.nav.syfo.testutil.*
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val azureAdV2Client = AzureAdV2Client(
                azureAppClientId = externalMockEnvironment.environment.azureAppClientId,
                azureAppClientSecret = externalMockEnvironment.environment.azureAppClientSecret,
                azureTokenEndpoint = externalMockEnvironment.environment.azureTokenEndpoint,
            )
            val pdlClient = PdlClient(
                azureAdV2Client = azureAdV2Client,
                pdlClientId = externalMockEnvironment.environment.pdlClientId,
                pdlUrl = externalMockEnvironment.environment.pdlUrl,
            )

            val identhendelseService = IdenthendelseService(
                database = database,
                pdlClient = pdlClient,
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

                    val allPersonoppgaver = database.connection.getAllPersonoppgaver()
                    allPersonoppgaver.size shouldBeEqualTo 3
                    allPersonoppgaver.filter { it.fnr == personOppgaveOldIdent.personIdent.value }.size shouldBeEqualTo 0
                    allPersonoppgaver.filter { it.fnr == personOppgaveNewIdent.personIdent.value }.size shouldBeEqualTo 2
                }

                // TODO: En egen test for at alle tabeller endres?
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

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    val allMotesvar = database.connection.getAllMotesvar()
                    allMotesvar.size shouldBeEqualTo 3
                    allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarOldIdent.arbeidstakerIdent.value }.size shouldBeEqualTo 0
                    allMotesvar.filter { it.arbeidstakerIdent == dialogmotesvarNewIdent.arbeidstakerIdent.value }.size shouldBeEqualTo 2
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

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    val allDialogmotestatusendring = database.connection.getAllDialogmoteStatusendring()
                    allDialogmotestatusendring.size shouldBeEqualTo 3
                    allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringOldIdent.personIdent.value }.size shouldBeEqualTo 0
                    allDialogmotestatusendring.filter { it.arbeidstakerIdent == dialogmotestatusendringNewIdent.personIdent.value }.size shouldBeEqualTo 2
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
                    database.connection.use { connection ->
                        connection.createPersonOppgave(
                            kOppfolgingsplanLPS = kOppfolgingsplanLPS,
                            type = PersonOppgaveType.OPPFOLGINGSPLANLPS,
                        )
                    }

                    val currentPersonOppgave = database.getPersonOppgaveList(oldIdent)
                    currentPersonOppgave.size shouldBeEqualTo 1

                    runBlocking {
                        assertFailsWith(IllegalStateException::class) {
                            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                        }
                    }
                }
            }
        }
    }
})
