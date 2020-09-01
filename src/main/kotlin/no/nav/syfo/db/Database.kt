package no.nav.syfo.db

import com.zaxxer.hikari.HikariConfig

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.ResultSet

enum class Role {
    ADMIN, USER, READONLY;

    override fun toString() = name.toLowerCase()
}

data class DbConfig(
    val jdbcUrl: String,
    val password: String,
    val username: String,
    val databaseName: String,
    val poolSize: Int = 2,
    val runMigrationsOninit: Boolean = true
)

class DevDatabase(daoConfig: DbConfig) : Database(daoConfig, null)

class ProdDatabase(daoConfig: DbConfig, initBlock: (context: Database) -> Unit) : Database(daoConfig, initBlock) {

    override fun runFlywayMigrations(jdbcUrl: String, username: String, password: String): Int = Flyway.configure().run {
        dataSource(jdbcUrl, username, password)
        initSql("SET ROLE \"${daoConfig.databaseName}-${Role.ADMIN}\"") // required for assigning proper owners for the tables
        load().migrate()
    }
}

/**
 * Base Database implementation.
 * Hooks up the database with the provided configuration/credentials
 */
abstract class Database(val daoConfig: DbConfig, private val initBlock: ((context: Database) -> Unit)?) : DatabaseInterface {

    var dataSource: HikariDataSource

    init {

        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = daoConfig.jdbcUrl
            username = daoConfig.username
            password = daoConfig.password
            maximumPoolSize = daoConfig.poolSize
            minimumIdle = 1
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        })

        afterInit()
    }

    fun updateCredentials(username: String, password: String) {
        dataSource.apply {
            hikariConfigMXBean.setPassword(password)
            hikariConfigMXBean.setUsername(username)
            hikariPoolMXBean.softEvictConnections()
        }
    }

    override val connection: Connection
        get() = dataSource.connection

    private fun afterInit() {
        if (daoConfig.runMigrationsOninit) {
            runFlywayMigrations(daoConfig.jdbcUrl, daoConfig.username, daoConfig.password)
        }
        initBlock?.let { run(it) }
    }

    open fun runFlywayMigrations(jdbcUrl: String, username: String, password: String) = Flyway.configure().run {
        dataSource(jdbcUrl, username, password)
        load().migrate()
    }
}

fun <T> ResultSet.toList(mapper: ResultSet.() -> T) = mutableListOf<T>().apply {
    while (next()) {
        add(mapper())
    }
}

interface DatabaseInterface {
    val connection: Connection
}
