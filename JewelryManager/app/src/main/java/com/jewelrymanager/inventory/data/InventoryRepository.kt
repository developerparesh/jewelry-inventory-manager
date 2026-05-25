package com.jewelrymanager.inventory.data

import kotlinx.coroutines.flow.Flow

class InventoryRepository(private val inventoryDao: InventoryDao) {

    val allItems: Flow<List<JewelryItem>> = inventoryDao.getAllItems()

    fun getItem(id: Int): Flow<JewelryItem> = inventoryDao.getItem(id)

    suspend fun getItemSingle(id: Int): JewelryItem? = inventoryDao.getItemSingle(id)

    suspend fun insertItem(item: JewelryItem) {
        inventoryDao.insert(item)
    }

    suspend fun updateItem(item: JewelryItem) {
        inventoryDao.update(item)
    }

    suspend fun deleteItem(item: JewelryItem) {
        inventoryDao.delete(item)
    }
}
