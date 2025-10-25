import db.Accounts
import db.DatabaseFactory
import db.Transactions
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankIntegrationTest {
    // testcontainer с postgresql
    @Container
    private val postgres =
        PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

    @BeforeAll
    fun setup() {
        postgres.start()

        DatabaseFactory.init(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password,
        )

        transaction {
            SchemaUtils.create(Accounts, Transactions)
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @Test
    fun `should perform transfer safely with row locking`() {
        val acc1 = Bank.createAccount(1000.0)
        val acc2 = Bank.createAccount(500.0)

        // эмулируем конкурентные переводы
        val t1 = Thread { Bank.transfer(acc1, acc2, 200.0) }
        val t2 = Thread { Bank.transfer(acc1, acc2, 100.0) }

        t1.start()
        t2.start()
        t1.join()
        t2.join()

        val all = Bank.getAllAccounts().toMap()
        val total = all.values.sum()

        // проверяем, что деньги не "потерялись"
        Assertions.assertEquals(1500.0, total, 0.001)
    }
}
