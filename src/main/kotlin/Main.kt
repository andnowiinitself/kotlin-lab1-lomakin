import db.Accounts
import db.DatabaseFactory
import db.Transactions
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

fun main() {
    DatabaseFactory.init()

    transaction {
        SchemaUtils.create(Accounts, Transactions)
    }

    val accountService = Account()

    println("Creating accounts...")
    val acc1 = Bank.createAccount(1000.0)
    val acc2 = Bank.createAccount(500.0)
    println("Accounts created: $acc1 and $acc2")

    println("Depositing 200.0 to account $acc1")
    accountService.deposit(acc1, 200.0)

    println("Transferring 300.0 from $acc1 to $acc2")
    Bank.transfer(acc1, acc2, 300.0)

    println("\nAll accounts:")
    Bank.getAllAccounts().forEach { (id, balance) ->
        println("Account #$id | Balance: $balance")
    }

    println("\nStatements:")
    println(accountService.getStatement(acc1))
    println(accountService.getStatement(acc2, StatementFormat.PDF))
}

enum class StatementFormat { TXT, PDF }

enum class TransactionType {
    DEPOSIT,
    WITHDRAWAL,
}

data class Transaction(
    val amount: Double,
    val type: TransactionType,
    val description: String,
    val timestamp: Long,
)

class InsufficientFundsException(message: String) : Exception(message)

class InvalidAmountException(message: String) : Exception(message)

class AccountDoesNotExistException(message: String) : Exception(message)

class Account {
    fun createAccount(initialBalance: Double = 0.0): Int =
        transaction {
            Accounts.insertAndGetId {
                it[balance] = initialBalance
            }.value
        }

    fun getBalance(accountId: Int): Double {
        return Accounts.selectAll()
            .where { Accounts.id eq accountId }
            .singleOrNull()?.get(Accounts.balance)
            ?: throw AccountDoesNotExistException("Account $accountId does not exist")
    }

    fun deposit(
        accountId: Int,
        amount: Double,
        description: String = "Deposit",
    ) = transaction {
        if (amount <= 0.0) throw InvalidAmountException("deposit amount must be positive")

        val currentBalance = getBalance(accountId)
        Accounts.update({ Accounts.id eq accountId }) {
            it[balance] = currentBalance + amount
        }

        Transactions.insert {
            it[Transactions.accountId] = accountId
            it[Transactions.amount] = amount
            it[Transactions.type] = TransactionType.DEPOSIT.name
            it[Transactions.description] = description
            it[Transactions.timestamp] = Instant.now()
        }
    }

    fun withdraw(
        accountId: Int,
        amount: Double,
        description: String = "Withdrawal",
    ) = transaction {
        if (amount <= 0.0) throw InvalidAmountException("withdrawal amount must be positive")

        val currentBalance = getBalance(accountId)
        if (currentBalance < amount) throw InsufficientFundsException("not enough funds")

        Accounts.update({ Accounts.id eq accountId }) {
            it[balance] = currentBalance - amount
        }

        Transactions.insert {
            it[Transactions.accountId] = accountId
            it[Transactions.amount] = -amount
            it[Transactions.type] = TransactionType.WITHDRAWAL.name
            it[Transactions.description] = description
            it[Transactions.timestamp] = Instant.now()
        }
    }

    fun getStatement(
        accountId: Int,
        format: StatementFormat = StatementFormat.TXT,
    ): String {
        val statementText =
            transaction {
                val balance = getBalance(accountId)
                val txs =
                    Transactions.selectAll()
                        .where { Transactions.accountId eq accountId }
                        .map {
                            val type = it[Transactions.type]
                            val desc = it[Transactions.description]
                            val amount = it[Transactions.amount]
                            val time = it[Transactions.timestamp]
                            "Type: $type | Description: $desc | Amount: $amount | $time"
                        }

                buildString {
                    appendLine("=== Account statement for KBA#$accountId ===")
                    appendLine("Current balance: $balance\n")
                    appendLine("Transaction history:")
                    if (txs.isEmpty()) {
                        appendLine("No transactions found")
                    } else {
                        txs.forEach { appendLine(it) }
                    }
                    appendLine("=".repeat(40))
                }
            }

        return when (format) {
            StatementFormat.TXT -> statementText
            StatementFormat.PDF -> {
                val fileName = "statement_$accountId.pdf"
                createPdfStatement(fileName, statementText)
                "PDF statement generated: $fileName"
            }
        }
    }

    private fun createPdfStatement(
        fileName: String,
        content: String,
    ) {
        val document = com.lowagie.text.Document()
        val writer =
            com.lowagie.text.pdf.PdfWriter.getInstance(
                document,
                java.io.FileOutputStream(fileName),
            )
        document.open()
        document.add(com.lowagie.text.Paragraph(content))
        document.close()
        writer.close()
    }
}

object Bank {
    fun createAccount(initialBalance: Double = 0.0): Int = Account().createAccount(initialBalance)

    fun findAccount(accountId: Int): Boolean =
        transaction {
            Accounts.selectAll()
                .where { Accounts.id eq accountId }
                .any()
        }

    fun transfer(
        fromId: Int,
        toId: Int,
        amount: Double,
    ) = transaction {
        if (amount <= 0.0) throw InvalidAmountException("transfer amount must be positive")
        if (fromId == toId) return@transaction

        // блокировка строк в порядке ID, чтобы избежать deadlock
        val (first, second) = if (fromId < toId) fromId to toId else toId to fromId

        // блокируем обе строки с помощью FOR UPDATE
        val fromRow =
            Accounts.selectAll()
                .where { Accounts.id eq first }
                .forUpdate()
                .singleOrNull() ?: throw AccountDoesNotExistException("account $first not found")

        val toRow =
            Accounts.selectAll()
                .where { Accounts.id eq second }
                .forUpdate()
                .singleOrNull() ?: throw AccountDoesNotExistException("account $second not found")

        val fromBalance = fromRow[Accounts.balance]
        val toBalance = toRow[Accounts.balance]

        if (fromId == first && fromBalance < amount) {
            throw InsufficientFundsException("not enough funds for transfer")
        }

        Accounts.update({ Accounts.id eq fromId }) { it[balance] = fromBalance - amount }
        Accounts.update({ Accounts.id eq toId }) { it[balance] = toBalance + amount }

        // логируем операции
        Transactions.insert {
            it[Transactions.accountId] = fromId
            it[Transactions.amount] = -amount
            it[Transactions.type] = "TRANSFER_OUT"
            it[Transactions.description] = "Transfer to account $toId"
            it[Transactions.timestamp] = Instant.now()
        }
        Transactions.insert {
            it[Transactions.accountId] = toId
            it[Transactions.amount] = amount
            it[Transactions.type] = "TRANSFER_IN"
            it[Transactions.description] = "Transfer from account $fromId"
            it[Transactions.timestamp] = Instant.now()
        }
    }

    fun getAllAccounts(): List<Pair<Int, Double>> =
        transaction {
            Accounts.selectAll().map {
                it[Accounts.id].value to it[Accounts.balance]
            }
        }
}
