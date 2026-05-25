package com.jewelrymanager.inventory.ui

import androidx.lifecycle.*
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.data.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow("All")
    private val sortOrder = MutableStateFlow("Name (A-Z)")
    private val specialFilter = MutableStateFlow("None")

    val allItems: LiveData<List<JewelryItem>> = combine(
        repository.allItems,
        searchQuery,
        categoryFilter,
        sortOrder,
        specialFilter
    ) { items, query, category, sort, special ->
        var filtered = items

        if (category != "All") {
            filtered = filtered.filter { it.category == category }
        }

        when (special) {
            "Low Stock" -> filtered = filtered.filter { it.quantity <= 2 }
            "High Selling" -> filtered = filtered.filter { it.quantity > 10 }
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.name.contains(query, ignoreCase = true) || 
                it.sku.contains(query, ignoreCase = true)
            }
        }

        when (sort) {
            "Name (A-Z)" -> filtered = filtered.sortedBy { it.name }
            "Name (Z-A)" -> filtered = filtered.sortedByDescending { it.name }
            "Price (Low to High)" -> filtered = filtered.sortedBy { it.retailPrice }
            "Price (High to Low)" -> filtered = filtered.sortedByDescending { it.retailPrice }
            "Quantity (Low to High)" -> filtered = filtered.sortedBy { it.quantity }
            "Quantity (High to Low)" -> filtered = filtered.sortedByDescending { it.quantity }
        }

        filtered
    }.asLiveData()

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateCategoryFilter(category: String) {
        categoryFilter.value = category
    }

    fun updateSortOrder(sort: String) {
        sortOrder.value = sort
    }

    fun updateSpecialFilter(filter: String) {
        specialFilter.value = filter
    }

    fun getItem(id: Int): LiveData<JewelryItem> {
        return repository.getItem(id).asLiveData()
    }

    fun insertItem(
        sku: String,
        name: String,
        category: String,
        metal: String,
        carat: Double?,
        quantity: Int,
        costPrice: BigDecimal,
        retailPrice: BigDecimal,
        location: String,
        notes: String,
        imageUri: String?
    ) {
        val newItem = JewelryItem(
            sku = sku,
            name = name,
            category = category,
            metal = metal,
            carat = carat,
            quantity = quantity,
            costPrice = costPrice,
            retailPrice = retailPrice,
            location = location,
            notes = notes,
            imageUri = imageUri
        )
        viewModelScope.launch {
            repository.insertItem(newItem)
        }
    }

    fun updateItem(
        id: Int,
        sku: String,
        name: String,
        category: String,
        metal: String,
        carat: Double?,
        quantity: Int,
        costPrice: BigDecimal,
        retailPrice: BigDecimal,
        location: String,
        notes: String,
        imageUri: String?
    ) {
        viewModelScope.launch {
            val existingItem = repository.getItemSingle(id)
            val updatedItem = JewelryItem(
                id = id,
                sku = sku,
                name = name,
                category = category,
                metal = metal,
                carat = carat,
                quantity = quantity,
                costPrice = costPrice,
                retailPrice = retailPrice,
                location = location,
                notes = notes,
                imageUri = imageUri ?: existingItem?.imageUri
            )
            repository.updateItem(updatedItem)
        }
    }

    fun deleteItem(item: JewelryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
}

class InventoryViewModelFactory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return InventoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
