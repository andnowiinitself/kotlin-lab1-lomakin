import db.Accounts
import db.DatabaseFactory
import db.Transactions
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertFailsWith

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountTest {
    private val postgres =
        PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }

    private lateinit var db: Database
    private lateinit var account: Account

    @BeforeAll
    fun setup() {
        postgres.start()

        db =
            DatabaseFactory.initTest(
                url = postgres.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = postgres.username,
                password = postgres.password,
            )

        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(Accounts, Transactions)
        }

        account = Account()
    }

    @BeforeEach
    fun clearTables() {
        transaction(db) {
            Transactions.deleteAll()
            Accounts.deleteAll()
        }
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    @Test
    fun `createAccount should insert new account`() {
        transaction(db) {
            val id = account.createAccount(500.0)
            assertEquals(500.0, account.getBalance(id))
        }
    }

    @Test
    fun `deposit should increase balance`() {
        transaction(db) {
            val id = account.createAccount(100.0)
            account.deposit(id, 50.0)
            assertEquals(150.0, account.getBalance(id))
        }
    }

    @Test
    fun `deposit should throw for negative amount`() {
        transaction(db) {
            val id = account.createAccount(100.0)
            assertFailsWith<InvalidAmountException> {
                account.deposit(id, -10.0)
            }
        }
    }

    @Test
    fun `withdraw should decrease balance`() {
        transaction(db) {
            val id = account.createAccount(200.0)
            account.withdraw(id, 50.0)
            assertEquals(150.0, account.getBalance(id))
        }
    }

    @Test
    fun `withdraw should throw for insufficient funds`() {
        transaction(db) {
            val id = account.createAccount(100.0)
            assertFailsWith<InsufficientFundsException> {
                account.withdraw(id, 150.0)
            }
        }
    }

    @Test
    fun `withdraw should throw for negative amount`() {
        transaction(db) {
            val id = account.createAccount(100.0)
            assertFailsWith<InvalidAmountException> {
                account.withdraw(id, -10.0)
            }
        }
    }

    @Test
    fun `getBalance should throw for nonexistent account`() {
        transaction(db) {
            assertFailsWith<AccountDoesNotExistException> {
                account.getBalance(9999)
            }
        }
    }

    @Test
    fun `getStatement TXT should include transactions`() {
        transaction(db) {
            val id = account.createAccount(0.0)
            account.deposit(id, 100.0, "Test deposit")
            val statement = account.getStatement(id)
            assertTrue(statement.contains("Test deposit"))
            assertTrue(statement.contains("Account statement for KBA#$id"))
        }
    }

    @Test
    fun `getStatement PDF should create file`() {
        transaction(db) {
            val id = account.createAccount(0.0)
            account.deposit(id, 50.0)
            val msg = account.getStatement(id, StatementFormat.PDF)
            assertTrue(msg.contains(".pdf"))
            val fileName = "statement_$id.pdf"
            val file = File(fileName)
            assertTrue(file.exists())
            file.delete()
        }
    }
}
