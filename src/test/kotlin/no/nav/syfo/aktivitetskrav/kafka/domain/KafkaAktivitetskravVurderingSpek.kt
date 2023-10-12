package no.nav.syfo.aktivitetskrav.kafka.domain

import no.nav.syfo.aktivitetskrav.domain.AktivitetskravStatus
import no.nav.syfo.testutil.generators.generateKafkaAktivitetskravVurdering
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class KafkaAktivitetskravVurderingSpek : Spek({

    val kafkaAktivitetskravVurdering = generateKafkaAktivitetskravVurdering()

    describe(KafkaAktivitetskravVurdering::class.java.simpleName) {
        it("Will map correctly to AktivitetskravVurdering") {
            val aktivitetskravVurdering = kafkaAktivitetskravVurdering.toAktivitetskravVurdering()

            aktivitetskravVurdering.personIdent.value shouldBeEqualTo kafkaAktivitetskravVurdering.personIdent
            aktivitetskravVurdering.status.name shouldBeEqualTo kafkaAktivitetskravVurdering.status
            aktivitetskravVurdering.uuid.toString() shouldBeEqualTo kafkaAktivitetskravVurdering.uuid
            aktivitetskravVurdering.vurdertAv shouldBeEqualTo kafkaAktivitetskravVurdering.updatedBy
            aktivitetskravVurdering.createdAt shouldBeEqualTo kafkaAktivitetskravVurdering.createdAt
            aktivitetskravVurdering.sistVurdert shouldBeEqualTo kafkaAktivitetskravVurdering.sistVurdert
        }

        it("Will fail if updatedBy and sistVurdert are null when mapping to AktivitetskravVurdering") {
            val kafkaAktivitetskravVurderingNy = kafkaAktivitetskravVurdering.copy(
                status = AktivitetskravStatus.NY.name,
                updatedBy = null,
                sistVurdert = null,
            )

            assertFailsWith(NullPointerException::class) {
                kafkaAktivitetskravVurderingNy.toAktivitetskravVurdering()
            }
        }
    }
})
