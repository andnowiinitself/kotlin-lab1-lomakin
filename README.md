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

**Инкапсуляция:**
- Баланс счёта — `private var`
- История транзакций — приватная
- Доступ к балансу только через публичные методы

**Валидация:**
- Начальный баланс ≥ 0
- Суммы операций > 0
- Недостаточно средств → `InsufficientFundsException`

**Атомарность операций:**
- `transfer()` должен быть атомарным: либо обе операции (withdraw + deposit), либо ничего
- При исключении состояние счетов не меняется
- Обработка несуществующих счетов через nullable типы

**Отчётность:**
- `getStatement()` формирует строку через `StringBuffer`
- Включает текущий баланс и историю всех транзакций
- Формат произвольный, но читаемый

## Как запустить

### Через Kotlin REPL
kotlinc Main.kt -include-runtime -d Main.jar
java -jar Main.jar

### Через IntelliJ IDEA
1. Открыть проект
2. Запустить Main.kt

## Примеры использования

```kotlin
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
```

## Использованные технологии

kotlin 2.2.0