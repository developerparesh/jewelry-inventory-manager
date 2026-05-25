package com.jewelrymanager.inventory.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM jewelry_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<JewelryItem>>

    @Query("SELECT * FROM jewelry_items WHERE id = :id")
    fun getItem(id: Int): Flow<JewelryItem>

    @Query("SELECT * FROM jewelry_items WHERE id = :id")
    suspend fun getItemSingle(id: Int): JewelryItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: JewelryItem)

    @Update
    suspend fun update(item: JewelryItem)

    @Delete
    suspend fun delete(item: JewelryItem)
}
