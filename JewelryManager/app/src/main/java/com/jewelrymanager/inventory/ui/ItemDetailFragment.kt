package com.jewelrymanager.inventory.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.jewelrymanager.inventory.JewelryApplication
import com.jewelrymanager.inventory.R
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.databinding.FragmentItemDetailBinding

class ItemDetailFragment : Fragment() {

    private val navigationArgs: ItemDetailFragmentArgs by navArgs()
    private lateinit var item: JewelryItem

    private val viewModel: InventoryViewModel by activityViewModels {
        InventoryViewModelFactory(
            (requireActivity().application as JewelryApplication).repository
        )
    }

    private var _binding: FragmentItemDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sku = navigationArgs.itemSku
        viewModel.getItem(sku).observe(this.viewLifecycleOwner) { selectedItem ->
            if (selectedItem != null) {
                item = selectedItem
                bind(item)
            }
        }
    }

    private fun bind(item: JewelryItem) {
        binding.apply {
            itemImage.load(item.imageUri) {
                crossfade(enable = true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
            itemName.text = item.name
            itemSku.text = getString(R.string.sku_format, item.sku)
            itemCategory.text = item.category
            itemMetal.text = item.metal
            itemCarat.text = item.carat?.let { getString(R.string.carat_format, it) } ?: getString(R.string.not_applicable)
            itemPrice.text = getString(R.string.price_format, item.retailPrice)
            itemQuantity.text = item.quantity.toString()
            itemLocation.text = item.location
            itemNotes.text = item.notes

            editFab.setOnClickListener {
                val action = ItemDetailFragmentDirections.actionItemDetailFragmentToAddEditFragment(
                    itemSku = item.sku,
                    title = getString(R.string.save_item)
                )
                findNavController().navigate(action)
            }

            deleteButton.setOnClickListener {
                showDeleteConfirmationDialog()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_item_title)
            .setMessage(getString(R.string.delete_item_message, item.name))
            .setCancelable(false)
            .setNegativeButton(R.string.no) { _, _ -> }
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteItem()
            }
            .show()
    }

    private fun deleteItem() {
        viewModel.deleteItem(item)
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
