package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    // production
    private var initialized = false

    fun init() {
        if (initialized) return
        val config =
            HikariConfig().apply {
                jdbcUrl = "jdbc:postgresql://localhost:5432/bankdb"
                driverClassName = "org.postgresql.Driver"
                username = "postgres"
                password = "postgres"
                maximumPoolSize = 5
            }
        Database.connect(HikariDataSource(config))
        initialized = true
    }

    // тестовая версия
    fun initTest(
        url: String,
        driver: String,
        user: String,
        password: String,
    ): Database {
        val config =
            HikariConfig().apply {
                jdbcUrl = url
                driverClassName = driver
                this.username = user
                this.password = password
                maximumPoolSize = 5
            }
        val ds = HikariDataSource(config)
        return Database.connect(ds)
    }
}
