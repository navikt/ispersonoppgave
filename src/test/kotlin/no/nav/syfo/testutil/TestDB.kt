package no.nav.syfo.testutil

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.infrastructure.database.queries.PPersonOppgave
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.queries.PDialogmoteStatusendring
import no.nav.syfo.infrastructure.database.queries.toPDialogmoteStatusendring
import no.nav.syfo.infrastructure.database.queries.PDialogmotesvar
import no.nav.syfo.infrastructure.database.queries.toPDialogmotesvar
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.PersonOppgave
import no.nav.syfo.domain.PersonOppgaveType
import no.nav.syfo.infrastructure.kafka.oppfolgingsplanlps.KOppfolgingsplanLPS
import no.nav.syfo.infrastructure.database.queries.queryGetPersonOppgaverByFnr
import no.nav.syfo.infrastructure.database.queries.toPPersonOppgave
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.toOffsetDateTimeUTC
import org.flywaydb.core.Flyway
import java.sql.*
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class TestDB : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().setLocaleConfig("locale", "en_US").start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {
        Flyway.configure().run {
            dataSource(pg.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}

fun DatabaseInterface.dropData() {
    val queryList = listOf(
        """
        DELETE FROM PERSON_OPPGAVE
        """.trimIndent(),
        """
        DELETE FROM MOTESVAR
        """.trimIndent(),
        """
        DELETE FROM DIALOGMOTE_STATUSENDRING
        """.trimIndent(),
        """
        DELETE FROM SYKMELDING_PERSONOPPGAVE 
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

fun DatabaseInterface.getPersonOppgaveList(fodselnummer: PersonIdent): List<PPersonOppgave> {
    return connection.use { connection ->
        connection.prepareStatement(queryGetPersonOppgaverByFnr).use {
            it.setString(1, fodselnummer.value)
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

fun DatabaseInterface.getAllPersonoppgaver(): List<PPersonOppgave> {
    val query = "SELECT * FROM person_oppgave"
    return connection.use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

fun DatabaseInterface.getAllMotesvar(): List<PDialogmotesvar> {
    val query = "SELECT * FROM motesvar"
    return connection.use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPDialogmotesvar() }
        }
    }
}

fun DatabaseInterface.getAllDialogmoteStatusendring(): List<PDialogmoteStatusendring> {
    val query = "SELECT * FROM dialogmote_statusendring"
    return connection.use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPDialogmoteStatusendring() }
        }
    }
}

fun DatabaseInterface.createPersonOppgave(
    kOppfolgingsplanLPS: KOppfolgingsplanLPS,
    type: PersonOppgaveType,
): Pair<Int, UUID> {
    return connection.use { connection ->
        val uuid = UUID.randomUUID().toString()
        val now = Timestamp.from(Instant.now())
        val personIdList = connection.prepareStatement(queryCreatePersonOppgave).use {
            it.setString(1, uuid)
            it.setString(2, kOppfolgingsplanLPS.uuid)
            it.setString(3, kOppfolgingsplanLPS.fodselsnummer)
            it.setString(4, kOppfolgingsplanLPS.virksomhetsnummer)
            it.setString(5, type.name)
            it.setTimestamp(6, now)
            it.setTimestamp(7, now)
            it.setBoolean(8, false)
            it.executeQuery().toList { getInt("id") }
        }
        if (personIdList.size != 1) {
            throw SQLException("Creating person failed, no rows affected.")
        }
        connection.commit()
        Pair(personIdList.first(), UUID.fromString(uuid))
    }
}

fun Connection.createPersonOppgave(
    personoppgave: PersonOppgave,
) {
    val personIdList = prepareStatement(queryCreatePersonOppgave).use {
        it.setString(1, personoppgave.uuid.toString())
        it.setString(2, personoppgave.referanseUuid.toString())
        it.setString(3, personoppgave.personIdent.value)
        it.setString(4, personoppgave.virksomhetsnummer?.value ?: "")
        it.setString(5, personoppgave.type.name)
        it.setObject(6, personoppgave.opprettet.toOffsetDateTimeUTC())
        it.setObject(7, personoppgave.sistEndret.toOffsetDateTimeUTC())
        it.setBoolean(8, personoppgave.publish)
        it.executeQuery().toList { getInt("id") }
    }

    if (personIdList.size != 1) {
        throw SQLException("Creating person failed, no rows affected.")
    }
}

const val queryCreatePersonOppgave =
    """INSERT INTO PERSON_OPPGAVE (
        id,
        uuid,
        referanse_uuid,
        fnr,
        virksomhetsnummer,
        type,
        opprettet,
        sist_endret, 
        publish) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
    """

fun DatabaseInterface.getDuplicateCount(sykmeldingId: UUID) =
    connection.use {
        it.prepareStatement("SELECT s.duplicate_count FROM sykmelding_personoppgave s INNER JOIN person_oppgave p ON (s.personoppgave_id=p.id) WHERE p.referanse_uuid=?")
            .use {
                it.setString(1, sykmeldingId.toString())
                it.executeQuery().toList { getInt(1) }.firstOrNull()
            }
    }

fun DatabaseInterface.updateCreatedAt(sykmeldingId: UUID, newCreatedAt: OffsetDateTime) =
    connection.use {
        val personoppgaveId = it.prepareStatement("SELECT id FROM PERSON_OPPGAVE WHERE referanse_uuid=?").use {
            it.setString(1, sykmeldingId.toString())
            it.executeQuery().toList { getInt("id") }
        }.first()
        it.prepareStatement("UPDATE sykmelding_personoppgave SET created_at=? WHERE personoppgave_id=?").use {
            it.setObject(1, newCreatedAt)
            it.setInt(2, personoppgaveId)
            it.executeUpdate()
        }
        it.commit()
    }
