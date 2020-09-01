package no.nav.syfo.personoppgave.oppfolgingsplanlps

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.util.InternalAPI
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.UserConstants.ARBEIDSTAKER_FNR
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@InternalAPI
object OppfolgingsplanLPSServiceSpek : Spek({

    describe("OppfolgingsplanLPSService") {
        val database by lazy { TestDB() }
        val oppfolgingsplanLPSService = OppfolgingsplanLPSService(database)

        afterGroup {
            database.stop()
        }

        with(TestApplicationEngine()) {
            start()

            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            beforeEachTest {
                database.connection.dropData()
            }

            afterEachTest {
                database.connection.dropData()
            }

            describe("Receive kOppfolgingsplanLPSNAV") {
                it("should create a new PPersonOppgave with correct type") {
                    val kOppfolgingsplanLPSNAV = generateKOppfolgingsplanLPSNAV

                    oppfolgingsplanLPSService.receiveOppfolgingsplanLPS(kOppfolgingsplanLPSNAV)

                    val personListe = database.connection.getPersonOppgaveList(ARBEIDSTAKER_FNR)

                    personListe.size shouldBe 1
                    personListe[0].fnr shouldEqual kOppfolgingsplanLPSNAV.getFodselsnummer()
                    personListe[0].virksomhetsnummer shouldEqual kOppfolgingsplanLPSNAV.getVirksomhetsnummer()
                    personListe[0].type shouldEqual PersonOppgaveType.OPPFOLGINGSPLANLPS.name
                }
            }
        }
    }
})
