package no.nav.syfo.testutil

import no.nav.syfo.db.*
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.oppfolgingsplan.avro.KOppfolgingsplanLPSNAV
import no.nav.syfo.personoppgave.*
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.domain.PersonOppgaveType
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.*
import java.time.Instant
import java.util.*

class TestDB : DatabaseInterface {

    private val container = PostgreSQLContainer<Nothing>("postgres:11.1").apply {
        withDatabaseName("db_test")
        withUsername("username")
        withPassword("password")
    }

    private var db: DatabaseInterface
    override val connection: Connection
        get() = db.connection.apply { autoCommit = false }

    init {
        container.start()
        db = DevDatabase(
            DbConfig(
                jdbcUrl = container.jdbcUrl,
                username = "username",
                password = "password",
                databaseName = "db_test"
            )
        )
    }

    fun stop() {
        container.stop()
    }
}

fun Connection.dropData() {
    val query = "DELETE FROM PERSON_OPPGAVE"
    use { connection ->
        connection.prepareStatement(query).executeUpdate()
        connection.commit()
    }
}

fun Connection.getPersonOppgaveList(fodselnummer: Fodselsnummer): List<PPersonOppgave> {
    return use { connection ->
        connection.prepareStatement(queryGetPersonOppgaveListForFnr).use {
            it.setString(1, fodselnummer.value)
            it.executeQuery().toList { toPPersonOppgave() }
        }
    }
}

fun Connection.createPersonOppgave(
    kOppfolgingsplanLPSNAV: KOppfolgingsplanLPSNAV,
    type: PersonOppgaveType
): Pair<Int, UUID> {
    val uuid = UUID.randomUUID().toString()
    val now = Timestamp.from(Instant.now())

    use { connection ->
        val personIdList = connection.prepareStatement(queryCreatePersonOppgave).use {
            it.setString(1, uuid)
            it.setString(2, kOppfolgingsplanLPSNAV.getUuid())
            it.setString(3, kOppfolgingsplanLPSNAV.getFodselsnummer())
            it.setString(4, kOppfolgingsplanLPSNAV.getVirksomhetsnummer())
            it.setString(5, type.name)
            it.setTimestamp(6, now)
            it.setTimestamp(7, now)
            it.executeQuery().toList { getInt("id") }
        }

        if (personIdList.size != 1) {
            throw SQLException("Creating person failed, no rows affected.")
        }
        connection.commit()

        return Pair(personIdList.first(), UUID.fromString(uuid))
    }
}
