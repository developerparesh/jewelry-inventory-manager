package com.jewelrymanager.inventory.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.databinding.ItemJewelryBinding

class InventoryAdapter(private val onItemClicked: (JewelryItem) -> Unit) :
    ListAdapter<JewelryItem, InventoryAdapter.InventoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        return InventoryViewHolder(
            ItemJewelryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val current = getItem(position)
        holder.itemView.setOnClickListener {
            onItemClicked(current)
        }
        holder.bind(current)
    }

    class InventoryViewHolder(private var binding: ItemJewelryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: JewelryItem) {
            val context = binding.root.context
            binding.itemName.text = item.name
            binding.itemCategory.text = item.category
            binding.itemSku.text = item.sku
            binding.itemPrice.text = context.getString(com.jewelrymanager.inventory.R.string.price_format, item.retailPrice)
            binding.itemQuantity.text = context.getString(com.jewelrymanager.inventory.R.string.quantity_label, item.quantity)
            
            // Load image
            binding.itemImage.load(item.imageUri) {
                crossfade(enable = true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }

            if (item.quantity <= 2) {
                binding.lowStockBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.lowStockBadge.visibility = android.view.View.GONE
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<JewelryItem>() {
            override fun areItemsTheSame(oldItem: JewelryItem, newItem: JewelryItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: JewelryItem, newItem: JewelryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
