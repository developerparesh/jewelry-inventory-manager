package com.jewelrymanager.inventory.data

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface InventoryDao {
    @Query("SELECT * FROM jewelry_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<JewelryItem>>

    @Query("SELECT * FROM jewelry_items WHERE sku = :sku")
    fun getItem(sku: String): Flow<JewelryItem?>

    @Query("SELECT * FROM jewelry_items WHERE sku = :sku")
    suspend fun getItemSingle(sku: String): JewelryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: JewelryItem)

    @Update
    suspend fun update(item: JewelryItem)

    @Delete
    suspend fun delete(item: JewelryItem)

    @Query("SELECT * FROM jewelry_items WHERE sku = :sku")
    fun getItemBySku(sku: String): Flow<JewelryItem?>

    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT SUM(quantity * priceAtTime) FROM transactions WHERE type = 'EXIT'")
    fun getTotalSales(): Flow<BigDecimal?>

    @Query("SELECT DISTINCT partyName || ' (' || partyPhone || ')' FROM transactions WHERE type = 'EXIT' AND partyName IS NOT NULL AND partyName != 'Walk-in Customer'")
    fun getAllPastCustomers(): LiveData<List<String>>

    @Query("SELECT DISTINCT partyName || ' (' || partyPhone || ')' FROM transactions WHERE partyName IS NOT NULL AND partyName != 'Walk-in Customer' AND partyName != 'Regular Vendor' AND partyPhone != 'N/A'")
    fun getAllPastPartners(): LiveData<List<String>>
}