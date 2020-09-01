package no.nav.syfo.testutil

import no.nav.syfo.db.*
import no.nav.syfo.domain.Fodselsnummer
import no.nav.syfo.personoppgave.domain.PPersonOppgave
import no.nav.syfo.personoppgave.queryGetPersonOppgaveListForFnr
import no.nav.syfo.personoppgave.toPPersonOppgave
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class TestDB : DatabaseInterface {

    val container = PostgreSQLContainer<Nothing>("postgres:11.1").apply {
        withDatabaseName("db_test")
        withUsername("username")
        withPassword("password")
    }

    private var db: DatabaseInterface
    override val connection: Connection
        get() = db.connection.apply { autoCommit = false }

    init {
        container.start()
        db = DevDatabase(DbConfig(
            jdbcUrl = container.jdbcUrl,
            username = "username",
            password = "password",
            databaseName = "db_test"
        ))
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
