package com.jewelrymanager.inventory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

enum class TransactionType {
    ENTRY, EXIT
}

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sku: String,
    val type: TransactionType,
    val quantity: Int,
    val priceAtTime: BigDecimal,
    val timestamp: Long = System.currentTimeMillis(),
    val partyName: String? = null,
    val partyPhone: String? = null
)