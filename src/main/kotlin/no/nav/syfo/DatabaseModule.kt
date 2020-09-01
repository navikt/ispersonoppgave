package no.nav.syfo

import io.ktor.application.Application
import kotlinx.coroutines.launch
import no.nav.syfo.db.*
import no.nav.syfo.vault.Vault

lateinit var database: DatabaseInterface
fun Application.databaseModule() {
    isDev {
        database = DevDatabase(DbConfig(
            jdbcUrl = "jdbc:postgresql://localhost:5432/ispersonoppgave_dev",
            databaseName = "ispersonoppgave_dev",
            password = "password",
            username = "username")
        )

        state.running = true
    }

    isProd {
        val vaultCredentialService = VaultCredentialService()

        val newCredentials = vaultCredentialService.getNewCredentials(env.mountPathVault, env.databaseName, Role.USER)

        database = ProdDatabase(DbConfig(
            jdbcUrl = env.ispersonoppgaveDBURL,
            username = newCredentials.username,
            password = newCredentials.password,
            databaseName = env.databaseName,
            runMigrationsOninit = false)) { prodDatabase ->

            // i prod må vi kjøre flyway migrations med et eget sett brukernavn/passord
            vaultCredentialService.getNewCredentials(env.mountPathVault, env.databaseName, Role.ADMIN).let {
                prodDatabase.runFlywayMigrations(env.ispersonoppgaveDBURL, it.username, it.password)
            }

            vaultCredentialService.renewCredentialsTaskData = RenewCredentialsTaskData(env.mountPathVault, env.databaseName, Role.USER) {
                prodDatabase.updateCredentials(username = it.username, password = it.password)
            }

            state.running = true
        }

        launch(backgroundTasksContext) {
            try {
                Vault.renewVaultTokenTask(state)
            } finally {
                state.running = false
            }
        }

        launch(backgroundTasksContext) {
            try {
                vaultCredentialService.runRenewCredentialsTask { state.running }
            } finally {
                state.running = false
            }
        }
    }
}
