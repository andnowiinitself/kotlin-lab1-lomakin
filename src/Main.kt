import kotlin.jvm.Synchronized

enum class TransactionType {
    DEPOSIT, WITHDRAWAL
}

data class Transaction(
    val amount: Double,
    val type: TransactionType,
    val description: String,
    val timestamp: Long
)

class InsufficientFundsException(message: String) : Exception(message)

class InvalidAmountException(message: String) : Exception(message)

class AccountDoesNotExistException(message: String) : Exception(message)

class Account(val accountNumber: String, initialBalance: Double) {
    init {
        if (initialBalance < 0.0) throw InvalidAmountException("initial balance must be positive")
    }

    private var balance: Double = initialBalance
    private val transactions: MutableList<Transaction> = mutableListOf()

    @Synchronized
    fun getBalance(): Double = balance

    @Synchronized
    private fun setBalance(amount: Double) {balance = amount}

    @Synchronized
    fun deposit(amount: Double, description: String = "Deposit") {
        if (amount <= 0.0) throw InvalidAmountException("deposit amount must be positive")

        val transaction = Transaction(amount, TransactionType.DEPOSIT, description,
            System.currentTimeMillis())
        val currentBalance = getBalance()
        setBalance(currentBalance + amount)
        transactions.add(transaction)
    }

    @Synchronized
    fun withdraw(amount: Double, description: String = "Withdrawal") {
        if (amount <= 0.0) throw InvalidAmountException("withdrawal amount must be positive")
        if (getBalance() < amount) throw InsufficientFundsException("not enough funds in the balance")

        val transaction = Transaction(amount,TransactionType.WITHDRAWAL, description,
            System.currentTimeMillis())
        val currentBalance = getBalance()
        setBalance(currentBalance - amount)
        transactions.add(transaction)
    }

    // StringBuffer в отличие от StringBuilder является потокобезопасным (все методы
    // synchronized), однако из-за того и более медленным. StringBuffer в многопоточной
    // среде будет всегда выдавать неискаженный результат, даже в случае наличия доступа
    // к нему сразу у нескольких потоков, потому он и будет более предпочтителен.
    fun getStatement(): String {
        val buffer = StringBuffer()
        buffer.append("=== Account statement for KBA№$accountNumber ===\n") // KBA == KotlinBankAccount
        buffer.append("Current balance: ${getBalance()}\n\n")
        buffer.append("Transaction history:\n")
        if (transactions.isEmpty()) {buffer.append("No transactions found")}
        else {
            for (tn in transactions) {
                val type = when (tn.type) {
                    TransactionType.DEPOSIT -> "Deposit"
                    TransactionType.WITHDRAWAL -> "Withdrawal"
                }
                val time = java.time.Instant.ofEpochMilli(tn.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .toString()

                buffer.append("Type: $type | Description: ${tn.description} | Amount: ${tn.amount} | $time\n")
            }
        }
        buffer.append("\n").append("=".repeat(40)).append("\n")
        return buffer.toString()
    }
}

object Bank {
    private val accounts: MutableMap<String, Account> = mutableMapOf()
    private var idCounter = 1000000

    fun createAccount(initialBalance: Double = 0.0): Account {
        val id = "${idCounter++}"
        val account = Account(id, initialBalance)
        accounts[id] = account
        return account
    }

    fun findAccount(accountNumber: String): Account? = accounts[accountNumber]

    fun transfer(fromAccNum: String, toAccNum: String, amount: Double) {
        if (amount <= 0.0) throw InvalidAmountException("transfer amount is less than or equal to zero") //nn
        if (fromAccNum == toAccNum) return

        val fromAccount =
            findAccount(fromAccNum) ?: throw
            AccountDoesNotExistException("sender's account does not exist")
        val toAccount =
            findAccount(toAccNum) ?: throw
            AccountDoesNotExistException("recipient's account does not exist")

        val first = if (fromAccount.accountNumber.toInt() < toAccount.accountNumber.toInt()) fromAccount else toAccount
        val second = if (fromAccount.accountNumber.toInt() < toAccount.accountNumber.toInt()) toAccount else fromAccount

        synchronized(first) {
            synchronized(second) {
                if (fromAccount.getBalance() < amount) throw
                InsufficientFundsException("not enough funds at the moment of transfer") //nn

                fromAccount.withdraw(amount, "Transfer to account ${toAccount.accountNumber}")
                toAccount.deposit(amount, "Transfer from account ${fromAccount.accountNumber}")
            }
        }
    }

    fun getAllAccounts(): List<Account> {
        return accounts.values.toList()
    }
}

fun main(){
    val acc1 = Bank.createAccount(1000.0)
    val acc2 = Bank.createAccount(500.0)

    acc1.deposit(200.0, "Salary")
    acc1.withdraw(50.0, "Groceries")

    Bank.transfer(acc1.accountNumber, acc2.accountNumber, 300.0)
//    Transferred 300.0 from acc1 to acc2

    println(acc1.getStatement())
//    === Account statement for KBA№1000000 ===
//    Current balance: 850.0
//
//    Transaction history:
//    Type: Deposit | Description: Salary | Amount: 200.0 | 2025-10-17T23:38:48.415
//    ...
}