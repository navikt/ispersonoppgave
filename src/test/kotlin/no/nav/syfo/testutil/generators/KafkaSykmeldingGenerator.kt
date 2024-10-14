package no.nav.syfo.testutil.generators

import no.nav.syfo.sykmelding.*
import no.nav.syfo.testutil.UserConstants
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

fun generateKafkaSykmelding(
    sykmeldingId: UUID,
    mottattTidspunkt: LocalDateTime = LocalDateTime.now(),
    behandletTidspunkt: LocalDateTime = LocalDateTime.now(),
    personNrPasient: String = UserConstants.ARBEIDSTAKER_FNR.value,
    personNrLege: String = "02020212345",
    fornavnLege: String = "Anne",
    etternavnLege: String = "Lege",
    telefonLege: String = "99999999",
    behandlerFnr: String = "02020212345",
    herId: String = "123",
    hprId: String = "321",
    legeHelsepersonellkategori: String = "LE",
    partnerreferanse: String? = "123",
    avsenderSystemNavn: String = "EPJ-systemet",
    kontorHerId: String = "404",
    meldingTilNAV: MeldingTilNAV?,
    tiltakNAV: String? = null,
    andreTiltak: String? = null,
) = ReceivedSykmeldingDTO(
    sykmelding = Sykmelding(
        id = sykmeldingId.toString(),
        msgId = UUID.randomUUID().toString(),
        medisinskVurdering = MedisinskVurdering(
            hovedDiagnose = null,
            biDiagnoser = emptyList(),
        ),
        behandletTidspunkt = behandletTidspunkt,
        behandler = Behandler(
            fornavn = fornavnLege,
            mellomnavn = "",
            etternavn = etternavnLege,
            fnr = behandlerFnr,
            hpr = hprId,
            her = herId,
            adresse = Adresse(
                gate = "",
                postnummer = 0,
                kommune = "",
                postboks = "",
                land = "",
            ),
            tlf = telefonLege,
        ),
        avsenderSystem = AvsenderSystem(
            navn = avsenderSystemNavn,
            versjon = "1.0",
        ),
        syketilfelleStartDato = LocalDate.now(),
        signaturDato = LocalDateTime.now(),
        navnFastlege = "",
        utdypendeOpplysninger = emptyMap(),
        meldingTilNAV = meldingTilNAV,
        andreTiltak = andreTiltak,
        meldingTilArbeidsgiver = "",
        tiltakNAV = tiltakNAV,
    ),
    personNrPasient = personNrPasient,
    personNrLege = personNrLege,
    legeHelsepersonellkategori = legeHelsepersonellkategori,
    legeHprNr = hprId,
    navLogId = "",
    msgId = UUID.randomUUID().toString(),
    legekontorOrgNr = null,
    legekontorHerId = kontorHerId,
    legekontorOrgName = "",
    mottattDato = mottattTidspunkt,
    partnerreferanse = partnerreferanse,
    fellesformat = "",
)
