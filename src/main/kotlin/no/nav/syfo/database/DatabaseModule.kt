package no.nav.syfo.database

import io.ktor.application.*
import kotlinx.coroutines.launch
import no.nav.syfo.*
import no.nav.syfo.vault.Vault

lateinit var database: DatabaseInterface
fun Application.databaseModule(
    applicationState: ApplicationState,
    environment: Environment,
) {
    isDev {
        database = DevDatabase(
            DbConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/ispersonoppgave_dev",
                databaseName = "ispersonoppgave_dev",
                password = "password",
                username = "username"
            )
        )
        applicationState.alive = true
    }

    isProd {
        val vaultCredentialService = VaultCredentialService()

        val newCredentials = vaultCredentialService.getNewCredentials(
            environment.mountPathVault,
            environment.databaseName,
            Role.USER,
        )

        database = ProdDatabase(
            DbConfig(
                jdbcUrl = environment.ispersonoppgaveDBURL,
                username = newCredentials.username,
                password = newCredentials.password,
                databaseName = environment.databaseName,
                runMigrationsOninit = false
            )
        ) { prodDatabase ->

            // i prod må vi kjøre flyway migrations med et eget sett brukernavn/passord
            vaultCredentialService.getNewCredentials(
                environment.mountPathVault,
                environment.databaseName,
                Role.ADMIN,
            ).let {
                prodDatabase.runFlywayMigrations(
                    environment.ispersonoppgaveDBURL,
                    it.username,
                    it.password,
                )
            }

            vaultCredentialService.renewCredentialsTaskData = RenewCredentialsTaskData(
                environment.mountPathVault,
                environment.databaseName,
                Role.USER,
            ) {
                prodDatabase.updateCredentials(
                    username = it.username,
                    password = it.password,
                )
            }

            applicationState.alive = true
        }

        launch(backgroundTasksContext) {
            try {
                Vault.renewVaultTokenTask(applicationState)
            } finally {
                applicationState.alive = false
            }
        }

        launch(backgroundTasksContext) {
            try {
                vaultCredentialService.runRenewCredentialsTask { applicationState.alive }
            } finally {
                applicationState.alive = false
            }
        }
    }
}
