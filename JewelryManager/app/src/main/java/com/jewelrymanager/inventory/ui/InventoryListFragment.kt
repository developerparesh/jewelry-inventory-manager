package com.jewelrymanager.inventory.ui

import android.content.ContentValues
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.jewelrymanager.inventory.JewelryApplication
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.databinding.FragmentInventoryListBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InventoryListFragment : Fragment() {

    private val viewModel: InventoryViewModel by activityViewModels {
        InventoryViewModelFactory(
            (requireActivity().application as JewelryApplication).repository
        )
    }

    private var _binding: FragmentInventoryListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupFilters()

        binding.fab.setOnClickListener {
            val bundle = Bundle().apply {
                putInt("itemId", -1)
            }
            findNavController().navigate(com.jewelrymanager.inventory.R.id.addEditFragment, bundle)
        }

        binding.exportPdfFab.setOnClickListener {
            viewModel.allItems.value?.let { items ->
                exportToPdf(items)
            } ?: run {
                Toast.makeText(requireContext(), "No items to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportToPdf(items: List<JewelryItem>) {
        val pdfDocument = PdfDocument()
        val paint = Paint()
        val titlePaint = Paint()

        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

        fun drawHeaders(canvas: Canvas, y: Float) {
            paint.isFakeBoldText = true
            canvas.drawText("SKU", 50f, y, paint)
            canvas.drawText("Name", 150f, y, paint)
            canvas.drawText("Category", 350f, y, paint)
            canvas.drawText("Qty", 450f, y, paint)
            canvas.drawText("Price", 500f, y, paint)
            canvas.drawLine(50f, y + 5f, 550f, y + 5f, paint)
            paint.isFakeBoldText = false
        }

        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        titlePaint.textSize = 18f
        titlePaint.isFakeBoldText = true
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("Jewelry Inventory Report", (pageWidth / 2).toFloat(), 50f, titlePaint)

        paint.textSize = 12f
        var currentY = 100f
        drawHeaders(canvas, currentY)
        currentY += 30f

        for (item in items) {
            if (currentY > 800) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = 50f
                drawHeaders(canvas, currentY)
                currentY += 30f
            }

            canvas.drawText(item.sku, 50f, currentY, paint)
            val displayName = if (item.name.length > 25) item.name.substring(0, 22) + "..." else item.name
            canvas.drawText(displayName, 150f, currentY, paint)
            canvas.drawText(item.category, 350f, currentY, paint)
            canvas.drawText(item.quantity.toString(), 450f, currentY, paint)
            canvas.drawText(String.format("$%.2f", item.retailPrice), 500f, currentY, paint)

            currentY += 25f
        }

        pdfDocument.finishPage(page)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Jewelry_Inventory_$timeStamp.pdf"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it).use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    Toast.makeText(requireContext(), getString(com.jewelrymanager.inventory.R.string.pdf_exported), Toast.LENGTH_LONG).show()
                }
            } else {
                val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                pdfDocument.writeTo(FileOutputStream(filePath))
                Toast.makeText(requireContext(), getString(com.jewelrymanager.inventory.R.string.pdf_exported), Toast.LENGTH_LONG).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(com.jewelrymanager.inventory.R.string.pdf_export_failed), Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
        }
    }

    private fun setupRecyclerView() {
        val adapter = InventoryAdapter { item ->
            val action = InventoryListFragmentDirections
                .actionInventoryListFragmentToItemDetailFragment(item.id)
            findNavController().navigate(action)
        }
        binding.recyclerView.adapter = adapter
        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            items.let { adapter.submitList(it) }
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.updateSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFilters() {
        // Category Filter
        val categories = resources.getStringArray(com.jewelrymanager.inventory.R.array.jewelry_categories)
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.categoryFilterSpinner.adapter = categoryAdapter
        binding.categoryFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.updateCategoryFilter(categories[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Special Filter (Low Stock / High Selling)
        val specialFilters = resources.getStringArray(com.jewelrymanager.inventory.R.array.special_filters)
        val specialAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, specialFilters)
        specialAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.specialFilterSpinner.adapter = specialAdapter
        binding.specialFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val filter = when (specialFilters[position]) {
                    "Low Stock" -> "Low Stock"
                    "High Selling" -> "High Selling"
                    else -> "None"
                }
                viewModel.updateSpecialFilter(filter)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Sort Spinner
        val sortOptions = resources.getStringArray(com.jewelrymanager.inventory.R.array.sort_options)
        val sortAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sortSpinner.adapter = sortAdapter
        binding.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.updateSortOrder(sortOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
