package com.jewelrymanager.inventory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "jewelry_items")
data class JewelryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sku: String,
    val name: String,
    val category: String,
    val metal: String,
    val carat: Double?,
    val quantity: Int,
    val costPrice: BigDecimal,
    val retailPrice: BigDecimal,
    val location: String,
    val notes: String,
    val imageUri: String? = null
)
