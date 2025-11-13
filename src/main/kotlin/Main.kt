import db.Accounts
import db.DatabaseFactory
import db.Transactions
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

fun main() {
    // usage scenario
    DatabaseFactory.init()

    transaction {
        SchemaUtils.create(Accounts, Transactions)
    }

    val accountService = Account()

    val acc1 = Bank.createAccount(1000.0)
    val acc2 = Bank.createAccount(500.0)

    accountService.deposit(acc1, 200.0)

    Bank.transfer(acc1, acc2, 300.0)

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
    private val logger: Logger = LoggerFactory.getLogger(Account::class.java)

    fun createAccount(initialBalance: Double = 0.0): Int {
        logger.info("Creating account with initial balance: {}", initialBalance)
        return transaction {
            val accountId =
                Accounts.insertAndGetId {
                    it[balance] = initialBalance
                }.value
            logger.info("Account created with ID: {}", accountId)
            accountId
        }
    }

    fun getBalance(accountId: Int): Double {
        logger.debug("Getting balance for account: {}", accountId)
        return Accounts.selectAll()
            .where { Accounts.id eq accountId }
            .singleOrNull()?.get(Accounts.balance)
            ?: run {
                logger.warn("Account {} does not exist", accountId)
                throw AccountDoesNotExistException("Account $accountId does not exist")
            }
    }

    fun deposit(
        accountId: Int,
        amount: Double,
        description: String = "Deposit",
    ) {
        logger.info("Depositing {} to account {} with description: {}", amount, accountId, description)
        if (amount <= 0.0) {
            logger.error("Invalid deposit amount: {}. Amount must be positive", amount)
            throw InvalidAmountException("deposit amount must be positive")
        }

        transaction {
            val currentBalance = getBalance(accountId)
            Accounts.update({ Accounts.id eq accountId }) {
                it[balance] = currentBalance + amount
            }

            logger.info("Deposit successful. New balance for account {}: {}", accountId, currentBalance + amount)

            Transactions.insert {
                it[Transactions.accountId] = accountId
                it[Transactions.amount] = amount
                it[Transactions.type] = TransactionType.DEPOSIT.name
                it[Transactions.description] = description
                it[Transactions.timestamp] = Instant.now()
            }
        }
    }

    fun withdraw(
        accountId: Int,
        amount: Double,
        description: String = "Withdrawal",
    ) {
        logger.info("Withdrawing {} from account {} with description: {}", amount, accountId, description)
        if (amount <= 0.0) {
            logger.error("Invalid withdrawal amount: {}. Amount must be positive", amount)
            throw InvalidAmountException("withdrawal amount must be positive")
        }

        transaction {
            val currentBalance = getBalance(accountId)
            if (currentBalance < amount) {
                logger.warn("Insufficient funds for account {}: balance {}, requested {}", accountId, currentBalance, amount)
                throw InsufficientFundsException("not enough funds")
            }

            Accounts.update({ Accounts.id eq accountId }) {
                it[balance] = currentBalance - amount
            }

            logger.info("Withdrawal successful. New balance for account {}: {}", accountId, currentBalance - amount)

            Transactions.insert {
                it[Transactions.accountId] = accountId
                it[Transactions.amount] = -amount
                it[Transactions.type] = TransactionType.WITHDRAWAL.name
                it[Transactions.description] = description
                it[Transactions.timestamp] = Instant.now()
            }
        }
    }

    fun getStatement(
        accountId: Int,
        format: StatementFormat = StatementFormat.TXT,
    ): String {
        logger.info("Generating {} statement for account: {}", format, accountId)
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
            StatementFormat.TXT -> {
                logger.info("TXT statement generated for account {}: {}", accountId, statementText)
                statementText
            }
            StatementFormat.PDF -> {
                val fileName = "statement_$accountId.pdf"
                createPdfStatement(fileName, statementText)
                logger.info("PDF statement generated: {}", fileName)
                "PDF statement generated: $fileName"
            }
        }
    }

    private fun createPdfStatement(
        fileName: String,
        content: String,
    ) {
        logger.debug("Creating PDF statement file: {}", fileName)
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
        logger.info("PDF statement file created: {}", fileName)
    }
}

object Bank {
    private val logger: Logger = LoggerFactory.getLogger(Bank::class.java)

    fun createAccount(initialBalance: Double = 0.0): Int {
        logger.info("Creating account via Bank with initial balance: {}", initialBalance)
        return Account().createAccount(initialBalance)
    }

    fun findAccount(accountId: Int): Boolean {
        logger.debug("Searching for account: {}", accountId)
        return transaction {
            val exists =
                Accounts.selectAll()
                    .where { Accounts.id eq accountId }
                    .any()
            logger.debug("Account {} exists: {}", accountId, exists)
            exists
        }
    }

    fun transfer(
        fromId: Int,
        toId: Int,
        amount: Double,
    ) {
        logger.info("Transferring {} from account {} to account {}", amount, fromId, toId)
        if (amount <= 0.0) {
            logger.error("Invalid transfer amount: {}. Amount must be positive", amount)
            throw InvalidAmountException("transfer amount must be positive")
        }
        if (fromId == toId) {
            logger.warn("Transfer from account {} to same account {}, nothing to do", fromId, toId)
            return
        }

        transaction {
            // блокировка строк в порядке ID, чтобы избежать deadlock
            val (first, second) = if (fromId < toId) fromId to toId else toId to fromId

            // блокируем обе строки с помощью FOR UPDATE
            val fromRow =
                Accounts.selectAll()
                    .where { Accounts.id eq first }
                    .forUpdate()
                    .singleOrNull() ?: run {
                    logger.error("Account {} not found during transfer", first)
                    throw AccountDoesNotExistException("account $first not found")
                }

            val toRow =
                Accounts.selectAll()
                    .where { Accounts.id eq second }
                    .forUpdate()
                    .singleOrNull() ?: run {
                    logger.error("Account {} not found during transfer", second)
                    throw AccountDoesNotExistException("account $second not found")
                }

            val fromBalance = fromRow[Accounts.balance]
            val toBalance = toRow[Accounts.balance]

            if (fromId == first && fromBalance < amount) {
                logger.warn("Insufficient funds for transfer from account {}: balance {}, requested {}", fromId, fromBalance, amount)
                throw InsufficientFundsException("not enough funds for transfer")
            }

            Accounts.update({ Accounts.id eq fromId }) { it[balance] = fromBalance - amount }
            Accounts.update({ Accounts.id eq toId }) { it[balance] = toBalance + amount }

            logger.info(
                "Transfer completed successfully. New balance for account {}: {}, for account {}: {}",
                fromId,
                fromBalance - amount,
                toId,
                toBalance + amount,
            )

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
    }

    fun getAllAccounts(): List<Pair<Int, Double>> {
        logger.debug("Retrieving all accounts")
        return transaction {
            val accounts =
                Accounts.selectAll().map {
                    it[Accounts.id].value to it[Accounts.balance]
                }
            logger.info("Retrieved {} accounts", accounts.size)
            accounts
        }
    }
}
