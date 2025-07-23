package no.nav.syfo.personoppgave.infrastructure.database

import io.ktor.server.application.*
import no.nav.syfo.*

lateinit var database: DatabaseInterface
fun Application.databaseModule(
    environment: Environment,
) {
    isDev {
        database = Database(
            databaseConfig = DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/ispersonoppgave_dev",
                password = "password",
                username = "username",
            )
        )
    }

    isProd {
        database = Database(
            databaseConfig = DatabaseConfig(
                jdbcUrl = environment.jdbcUrl(),
                username = environment.ispersonoppgaveDbUsername,
                password = environment.ispersonoppgaveDbPassword,
            )
        )
    }
}
