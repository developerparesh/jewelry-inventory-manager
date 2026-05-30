package com.jewelrymanager.inventory.ui

import androidx.lifecycle.*
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.data.InventoryRepository
import com.jewelrymanager.inventory.data.Transaction
import com.jewelrymanager.inventory.data.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.math.BigDecimal

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {

    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions.asLiveData()
    val totalSales: LiveData<BigDecimal> = repository.totalSales.map { it ?: BigDecimal.ZERO }.asLiveData()
    val allPastCustomers: LiveData<List<String>> = repository.allPastCustomers
    val allPastPartners: LiveData<List<String>> = repository.allPastPartners
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
            "Newest First" -> filtered = filtered.sortedByDescending { it.dateAdded }
            "Oldest First" -> filtered = filtered.sortedBy { it.dateAdded }
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

    fun getItem(sku: String): LiveData<JewelryItem?> {
        return repository.getItem(sku).asLiveData()
    }

    fun getItemBySku(sku: String): LiveData<JewelryItem?> {
        return repository.getItemBySku(sku).asLiveData()
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
            val existingItem = repository.getItemSingle(sku)
            val updatedItem = JewelryItem(
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
                dateAdded = existingItem?.dateAdded ?: System.currentTimeMillis(),
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

    fun performTransaction(sku: String, type: TransactionType, quantity: Int) {
        viewModelScope.launch {
            val item = repository.getItemSingle(sku) ?: return@launch
            
            val transaction = Transaction(
                sku = sku,
                type = type,
                quantity = quantity,
                priceAtTime = if (type == TransactionType.EXIT) item.retailPrice else item.costPrice
            )
            repository.insertTransaction(transaction)

            val newQuantity = if (type == TransactionType.ENTRY) {
                item.quantity + quantity
            } else {
                (item.quantity - quantity).coerceAtLeast(0)
            }
            
            val updatedItem = item.copy(quantity = newQuantity)
            repository.updateItem(updatedItem)
        }
    }
    fun performSaleTransaction(sku: String, quantity: Int, customerName: String? = null, customerPhone: String? = null) {
        viewModelScope.launch {
            val item = repository.getItemSingle(sku) ?: return@launch

            val transaction = Transaction(
                sku = sku,
                type = TransactionType.EXIT,
                quantity = quantity,
                timestamp = System.currentTimeMillis(),
                priceAtTime = item.retailPrice,
                partyName = customerName,
                partyPhone = customerPhone
            )
            repository.insertTransaction(transaction)

            val newQuantity = (item.quantity - quantity).coerceAtLeast(0)
            val updatedItem = item.copy(quantity = newQuantity)
            repository.updateItem(updatedItem)
        }
    }

    fun performPurchaseTransaction(sku: String, quantity: Int, vendorName: String? = null, vendorPhone: String? = null) {
        viewModelScope.launch {
            val item = repository.getItemSingle(sku) ?: return@launch

            val transaction = Transaction(
                sku = sku,
                type = TransactionType.ENTRY,
                quantity = quantity,
                timestamp = System.currentTimeMillis(),
                priceAtTime = item.costPrice,
                partyName = vendorName,
                partyPhone = vendorPhone
            )
            repository.insertTransaction(transaction)

            val newQuantity = item.quantity + quantity
            val updatedItem = item.copy(quantity = newQuantity)
            repository.updateItem(updatedItem)
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