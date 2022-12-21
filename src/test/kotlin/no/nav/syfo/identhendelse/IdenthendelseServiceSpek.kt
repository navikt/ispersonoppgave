package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.*
import no.nav.syfo.client.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.pdl.PdlClient
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
                    val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
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
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    val updatedPersonOppgave = database.getPersonOppgaveList(newIdent)
                    updatedPersonOppgave.first().fnr shouldBeEqualTo newIdent.value

                    val oldPersonOppgave = database.getPersonOppgaveList(oldIdent)
                    oldPersonOppgave.size shouldBeEqualTo 0
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
