package com.jewelrymanager.inventory.data

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val inventoryDao: InventoryDao) {

    val allItems: Flow<List<JewelryItem>> = inventoryDao.getAllItems()

    val allTransactions: Flow<List<Transaction>> = inventoryDao.getAllTransactions()

    val totalSales: Flow<java.math.BigDecimal?> = inventoryDao.getTotalSales()

    val allPastCustomers: LiveData<List<String>> = inventoryDao.getAllPastCustomers()
    val allPastPartners: LiveData<List<String>> = inventoryDao.getAllPastPartners()

    fun getItem(sku: String): Flow<JewelryItem?> = inventoryDao.getItem(sku)

    suspend fun getItemSingle(sku: String): JewelryItem? = inventoryDao.getItemSingle(sku)

    fun getItemBySku(sku: String): Flow<JewelryItem?> = inventoryDao.getItemBySku(sku)

    suspend fun insertItem(item: JewelryItem) {
        inventoryDao.insert(item)
    }

    suspend fun insertTransaction(transaction: Transaction) {
        inventoryDao.insertTransaction(transaction)
    }

    suspend fun updateItem(item: JewelryItem) {
        inventoryDao.update(item)
    }

    suspend fun deleteItem(item: JewelryItem) {
        inventoryDao.delete(item)
    }
}