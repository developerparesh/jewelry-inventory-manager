package com.jewelrymanager.inventory

import android.app.Application
import com.jewelrymanager.inventory.data.InventoryDatabase
import com.jewelrymanager.inventory.data.InventoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class JewelryApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { InventoryDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { InventoryRepository(database.inventoryDao()) }
}
