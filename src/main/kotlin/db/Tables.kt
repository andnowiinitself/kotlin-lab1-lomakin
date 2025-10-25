package db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Accounts : IntIdTable("accounts") {
    val balance = double("balance")
}

object Transactions : Table("transactions") {
    val id = integer("id").autoIncrement()
    val accountId = integer("account_id").references(Accounts.id)
    val amount = double("amount")
    val type = varchar("type", 50)
    val description = varchar("description", 255)
    val timestamp = timestamp("timestamp")
    override val primaryKey = PrimaryKey(id)
}
