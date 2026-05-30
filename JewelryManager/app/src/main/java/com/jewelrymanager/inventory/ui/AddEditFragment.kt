package com.jewelrymanager.inventory.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.jewelrymanager.inventory.JewelryApplication
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.databinding.FragmentAddEditBinding
import java.math.BigDecimal

class AddEditFragment : Fragment() {

    private val navigationArgs: AddEditFragmentArgs by navArgs()
    private var item: JewelryItem? = null
    private var selectedImageUri: String? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it.toString()
            binding.itemImage.load(it)
        }
    }

    private val viewModel: InventoryViewModel by activityViewModels {
        InventoryViewModelFactory(
            (requireActivity().application as JewelryApplication).repository
        )
    }

    private var _binding: FragmentAddEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        
        binding.imageCard.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        val sku = navigationArgs.itemSku
        if (sku != null) {
            viewModel.getItem(sku).observe(this.viewLifecycleOwner) { selectedItem ->
                if (selectedItem != null) {
                    item = selectedItem
                    bind(item!!)
                }
            }
        } else {
            binding.saveButton.setOnClickListener {
                if (validateInput()) {
                    addNewItem()
                }
            }
        }
    }

    private fun setupSpinners() {
        val categories = resources.getStringArray(com.jewelrymanager.inventory.R.array.jewelry_categories)
            .filter { it != "All" }.toTypedArray()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.itemCategorySpinner.adapter = adapter
    }

    private fun bind(item: JewelryItem) {
        binding.apply {
            selectedImageUri = item.imageUri
            itemImage.load(item.imageUri) {
                crossfade(enable = true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }
            itemName.setText(item.name)
            itemSku.setText(item.sku)
            itemSku.isEnabled = false 
            itemPrice.setText(item.retailPrice.toString())
            itemQuantity.setText(item.quantity.toString())
            itemMetal.setText(item.metal)
            itemCarat.setText(item.carat?.toString() ?: "")
            itemLocation.setText(item.location)
            itemNotes.setText(item.notes)
            
            val categories = resources.getStringArray(com.jewelrymanager.inventory.R.array.jewelry_categories)
                .filter { it != "All" }
            val selection = categories.indexOf(item.category)
            if (selection >= 0) {
                itemCategorySpinner.setSelection(selection)
            }

            saveButton.setOnClickListener { 
                if (validateInput()) {
                    updateItem()
                }
            }
        }
    }

    private fun validateInput(): Boolean {
        var isValid = true
        binding.apply {
            if (itemName.text.isNullOrBlank()) {
                nameInputLayout.error = "Name is required"
                isValid = false
            } else {
                nameInputLayout.error = null
            }

            if (itemSku.text.isNullOrBlank()) {
                skuInputLayout.error = "SKU is required"
                isValid = false
            } else {
                skuInputLayout.error = null
            }

            if (itemPrice.text.isNullOrBlank()) {
                priceInputLayout.error = "Price is required"
                isValid = false
            } else {
                priceInputLayout.error = null
            }

            if (itemQuantity.text.isNullOrBlank()) {
                quantityInputLayout.error = "Quantity is required"
                isValid = false
            } else {
                quantityInputLayout.error = null
            }
        }
        return isValid
    }

    private fun addNewItem() {
        binding.apply {
            val price = itemPrice.text.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
            viewModel.insertItem(
                itemSku.text.toString(),
                itemName.text.toString(),
                itemCategorySpinner.selectedItem.toString(),
                itemMetal.text.toString(),
                itemCarat.text.toString().toDoubleOrNull(),
                itemQuantity.text.toString().toIntOrNull() ?: 0,
                price.multiply(BigDecimal("0.7")), 
                price,
                itemLocation.text.toString(),
                itemNotes.text.toString(),
                selectedImageUri
            )
        }
        findNavController().navigateUp()
    }

    private fun updateItem() {
        binding.apply {
            val price = itemPrice.text.toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
            viewModel.updateItem(
                itemSku.text.toString(),
                itemName.text.toString(),
                itemCategorySpinner.selectedItem.toString(),
                itemMetal.text.toString(),
                itemCarat.text.toString().toDoubleOrNull(),
                itemQuantity.text.toString().toIntOrNull() ?: 0,
                price.multiply(BigDecimal("0.7")), 
                price,
                itemLocation.text.toString(),
                itemNotes.text.toString(),
                selectedImageUri
            )
        }
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}