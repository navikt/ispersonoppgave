package no.nav.syfo.dialogmotestatusendring

import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import no.nav.syfo.dialogmotestatusendring.domain.DialogmoteStatusendringType
import no.nav.syfo.dialogmotestatusendring.kafka.KafkaDialogmoteStatusendring
import no.nav.syfo.testutil.*
import no.nav.syfo.testutil.mock.mockPollConsumerRecords
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*

class DialogmoteStatusendringSpek : Spek({

    describe("Handling dialogmotesvar topic") {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment()
            val database = externalMockEnvironment.database
            val kafkaConsumer = mockk<KafkaConsumer<String, KDialogmoteStatusEndring>>()
            val kafkaDialogmoteStatusendring = KafkaDialogmoteStatusendring(database = database)

            beforeEachTest {
                every { kafkaConsumer.commitSync() } returns Unit
            }

            afterEachTest {
                database.dropData()
            }

            it("stores dialogmøtesvar from kafka in database") {
                val moteUuid = UUID.randomUUID()
                val instantNow = Instant.now()
                val offsetNow = OffsetDateTime.ofInstant(
                    instantNow,
                    ZoneOffset.UTC
                ).truncatedTo(ChronoUnit.MILLIS)
                val kDialogmoteStatusendring = KDialogmoteStatusEndring.newBuilder()
                    .setDialogmoteUuid(moteUuid.toString())
                    .setPersonIdent(UserConstants.ARBEIDSTAKER_FNR.value)
                    .setStatusEndringType(DialogmoteStatusendringType.AVLYST.name)
                    .setDialogmoteTidspunkt(instantNow)
                    .setStatusEndringTidspunkt(instantNow)
                    .setVirksomhetsnummer(UserConstants.VIRKSOMHETSNUMMER.value)
                    .setEnhetNr(UserConstants.NAV_ENHET)
                    .setTilfelleStartdato(instantNow)
                    .setNavIdent(UserConstants.VEILEDER_IDENT)
                    .setArbeidstaker(true)
                    .setArbeidsgiver(true)
                    .setSykmelder(true).build()

                kafkaConsumer.mockPollConsumerRecords(
                    recordValue = kDialogmoteStatusendring,
                    recordKey = moteUuid.toString()
                )

                kafkaDialogmoteStatusendring.pollAndProcessRecords(
                    kafkaConsumer = kafkaConsumer,
                )

                val allPDialogmoteStatusendring = database.connection.use { connection ->
                    connection.getDialogmoteStatusendring(
                        moteUuid = moteUuid
                    )
                }
                allPDialogmoteStatusendring.size shouldBeEqualTo 1
                val pDialogmoteStatusendring = allPDialogmoteStatusendring[0]
                pDialogmoteStatusendring.moteUuid shouldBeEqualTo moteUuid.toString()
                pDialogmoteStatusendring.endringTidspunkt shouldBeEqualTo offsetNow
                pDialogmoteStatusendring.type shouldBeEqualTo DialogmoteStatusendringType.AVLYST.name
                pDialogmoteStatusendring.arbeidstakerIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR.value
                pDialogmoteStatusendring.veilederIdent shouldBeEqualTo UserConstants.VEILEDER_IDENT
            }
        }
    }
})
