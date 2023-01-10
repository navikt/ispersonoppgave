package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.database.DatabaseInterface
import no.nav.syfo.database.toList
import no.nav.syfo.dialogmotestatusendring.domain.PDialogmoteStatusendring
import no.nav.syfo.dialogmotestatusendring.toPDialogmoteStatusendring
import no.nav.syfo.dialogmotesvar.domain.PDialogmotesvar
import no.nav.syfo.dialogmotesvar.toPDialogmotesvar
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.personoppgave.domain.*
import no.nav.syfo.personoppgave.oppfolgingsplanlps.kafka.KOppfolgingsplanLPS
import no.nav.syfo.personoppgave.queryGetPersonOppgaveListForFnr
import no.nav.syfo.personoppgave.toPPersonOppgave
import no.nav.syfo.util.toOffsetDateTimeUTC
import org.flywaydb.core.Flyway
import java.sql.*
import java.time.Instant
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

fun Connection.dropData() {
    val query = "DELETE FROM PERSON_OPPGAVE"
    use { connection ->
        connection.prepareStatement(query).executeUpdate()
        connection.commit()
    }
}

fun Connection.getPersonOppgaveList(fodselnummer: PersonIdent): List<PPersonOppgave> {
    return use { connection ->
        connection.prepareStatement(queryGetPersonOppgaveListForFnr).use {
            it.setString(1, fodselnummer.value)
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

fun Connection.getAllPersonoppgaver(): List<PPersonOppgave> {
    val query = "SELECT * FROM person_oppgave"
    return use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

fun Connection.getAllMotesvar(): List<PDialogmotesvar> {
    val query = "SELECT * FROM motesvar"
    return use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPDialogmotesvar() }
        }
    }
}

fun Connection.getAllDialogmoteStatusendring(): List<PDialogmoteStatusendring> {
    val query = "SELECT * FROM dialogmote_statusendring"
    return use { connection ->
        connection.prepareStatement(query).use {
            it.executeQuery().toList { toPDialogmoteStatusendring() }
        }
    }
}

fun Connection.createPersonOppgave(
    kOppfolgingsplanLPS: KOppfolgingsplanLPS,
    type: PersonOppgaveType
): Pair<Int, UUID> {
    val uuid = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())

    use { connection ->
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

        return Pair(personIdList.first(), UUID.fromString(uuid))
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
