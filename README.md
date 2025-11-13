# Лабораторная работа №1 — Kotlin Fundamentals

   **Студент:** Ломакин Глеб
   **Группа:** Null Safety Squad
   **Задание:** Управление транзакциями в банке с обработкой ошибок
   **Дата выполнения:** 2025-10-17


# Описание задачи

Задача заключается в написании модели для управления банковскими счетами и
транзакциями с акцентом на инкапсуляцию, обработку ошибок и безопасность
операций.

## Требования и ограничения

postgresql в docker compose, блокировки на уровне бд,
тесты через TestContainers, получение TXT и PDF в
getStatement, ну и чтоб работало хз

## Как запустить

### Через Kotlin REPL
kotlinc Main.kt -include-runtime -d Main.jar
java -jar Main.jar

### Через IntelliJ IDEA
1. Открыть проект
2. Запустить Main.kt

## Примеры использования

```kotlin
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
```

## Использованные технологии

kotlin 2.2.0, миллион dependency в build.gradle.kts
и llm'ки