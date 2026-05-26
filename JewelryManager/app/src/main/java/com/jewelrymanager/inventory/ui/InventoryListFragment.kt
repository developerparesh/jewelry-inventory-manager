package com.jewelrymanager.inventory.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.jewelrymanager.inventory.JewelryApplication
import com.jewelrymanager.inventory.data.JewelryItem
import com.jewelrymanager.inventory.data.Transaction
import com.jewelrymanager.inventory.data.TransactionType
import com.jewelrymanager.inventory.databinding.FragmentInventoryListBinding
import com.jewelrymanager.inventory.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
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

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission result ignored, we will just try to show the notification
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupRecyclerView()
        setupSearch()
        setupFilters()
        setupFabMenu()

        // Handle scanned barcode result
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>("scanned_barcode")
            ?.observe(viewLifecycleOwner) { barcode ->
                binding.searchEditText.setText(barcode)
                Toast.makeText(requireContext(), "Found barcode: $barcode", Toast.LENGTH_SHORT).show()
            }
    }

    private var isFabExpanded = false

    private fun setupFabMenu() {
        binding.mainFab.setOnClickListener {
            toggleFabMenu()
        }

        binding.addItemFabSmall.setOnClickListener {
            toggleFabMenu()
            val bundle = Bundle().apply {
                putInt("itemId", -1)
                putString("title", getString(com.jewelrymanager.inventory.R.string.add_item))
            }
            findNavController().navigate(com.jewelrymanager.inventory.R.id.addEditFragment, bundle)
        }

        binding.scanBarcodeFabSmall.setOnClickListener {
            toggleFabMenu()
            findNavController().navigate(com.jewelrymanager.inventory.R.id.action_inventoryListFragment_to_barcodeScanFragment)
        }

        binding.exportPdfFabSmall.setOnClickListener {
            toggleFabMenu()
            val items = viewModel.allItems.value ?: emptyList()
            val transactions = viewModel.allTransactions.value ?: emptyList()
            val totalSales = viewModel.totalSales.value ?: BigDecimal.ZERO
            exportToPdf(items, transactions, totalSales, "Full_Inventory")
        }

        binding.exportSalesFabSmall.setOnClickListener {
            toggleFabMenu()
            val transactions = (viewModel.allTransactions.value ?: emptyList()).filter { it.type == TransactionType.EXIT }
            val totalSales = viewModel.totalSales.value ?: BigDecimal.ZERO
            exportToPdf(emptyList(), transactions, totalSales, "Sales_Report")
        }
    }

    private fun toggleFabMenu() {
        isFabExpanded = !isFabExpanded
        if (isFabExpanded) {
            binding.mainFab.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            binding.addItemOption.visibility = View.VISIBLE
            binding.scanBarcodeOption.visibility = View.VISIBLE
            binding.exportPdfOption.visibility = View.VISIBLE
            binding.exportSalesOption.visibility = View.VISIBLE
        } else {
            binding.mainFab.setImageResource(android.R.drawable.ic_input_add)
            binding.addItemOption.visibility = View.GONE
            binding.scanBarcodeOption.visibility = View.GONE
            binding.exportPdfOption.visibility = View.GONE
            binding.exportSalesOption.visibility = View.GONE
        }
    }

    private fun exportToPdf(items: List<JewelryItem>, transactions: List<Transaction>, totalSales: BigDecimal, reportNamePrefix: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint()

            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

            // Inventory Page (Only if not empty)
            if (items.isNotEmpty()) {
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                titlePaint.textSize = 18f
                titlePaint.isFakeBoldText = true
                titlePaint.textAlign = Paint.Align.CENTER
                canvas.drawText("Inventory Report", (pageWidth / 2).toFloat(), 50f, titlePaint)

                paint.textSize = 12f
                var currentY = 100f
                
                fun drawInventoryHeaders(canvas: Canvas, y: Float) {
                    paint.isFakeBoldText = true
                    canvas.drawText("SKU", 50f, y, paint)
                    canvas.drawText("Name", 150f, y, paint)
                    canvas.drawText("Category", 350f, y, paint)
                    canvas.drawText("Qty", 450f, y, paint)
                    canvas.drawText("Price", 500f, y, paint)
                    canvas.drawLine(50f, y + 5f, 550f, y + 5f, paint)
                    paint.isFakeBoldText = false
                }

                drawInventoryHeaders(canvas, currentY)
                currentY += 30f

                for (item in items) {
                    if (currentY > 800) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = 50f
                        drawInventoryHeaders(canvas, currentY)
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
            }

            // Transactions Page
            if (transactions.isNotEmpty()) {
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var currentY = 50f
                
                titlePaint.textSize = 18f
                titlePaint.textAlign = Paint.Align.CENTER
                canvas.drawText(if (items.isEmpty()) "Sales Report" else "Inventory Movements & Sales", (pageWidth / 2).toFloat(), currentY, titlePaint)
                currentY += 40f

                paint.isFakeBoldText = true
                canvas.drawText("Total Sales: " + String.format("$%.2f", totalSales), 50f, currentY, paint)
                currentY += 40f

                fun drawTransactionHeaders(canvas: Canvas, y: Float) {
                    paint.isFakeBoldText = true
                    canvas.drawText("Date", 50f, y, paint)
                    canvas.drawText("SKU", 180f, y, paint)
                    canvas.drawText("Type", 300f, y, paint)
                    canvas.drawText("Qty", 400f, y, paint)
                    canvas.drawText("Value", 480f, y, paint)
                    canvas.drawLine(50f, y + 5f, 550f, y + 5f, paint)
                    paint.isFakeBoldText = false
                }

                drawTransactionHeaders(canvas, currentY)
                currentY += 30f

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                for (tx in transactions) {
                    if (currentY > 800) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        currentY = 50f
                        drawTransactionHeaders(canvas, currentY)
                        currentY += 30f
                    }

                    canvas.drawText(dateFormat.format(Date(tx.timestamp)), 50f, currentY, paint)
                    canvas.drawText(tx.sku, 180f, currentY, paint)
                    canvas.drawText(tx.type.name, 300f, currentY, paint)
                    canvas.drawText(tx.quantity.toString(), 400f, currentY, paint)
                    val txValue = tx.priceAtTime.multiply(BigDecimal(tx.quantity))
                    canvas.drawText(String.format("$%.2f", txValue), 480f, currentY, paint)

                    currentY += 25f
                }
                pdfDocument.finishPage(page)
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${reportNamePrefix}_$timeStamp.pdf"

            try {
                withContext(Dispatchers.IO) {
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
                            withContext(Dispatchers.Main) {
                                NotificationHelper.showExportNotification(requireContext(), it, fileName)
                            }
                        }
                    } else {
                        val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        pdfDocument.writeTo(FileOutputStream(filePath))
                        withContext(Dispatchers.Main) {
                            NotificationHelper.showExportNotification(requireContext(), filePath)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(com.jewelrymanager.inventory.R.string.pdf_exported), Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(com.jewelrymanager.inventory.R.string.pdf_export_failed), Toast.LENGTH_SHORT).show()
                }
            } finally {
                pdfDocument.close()
            }
        }
    }

    private fun setupRecyclerView() {
        val adapter = InventoryAdapter { item ->
            val action = InventoryListFragmentDirections
                .actionInventoryListFragmentToItemDetailFragment(item.sku)
            findNavController().navigate(action)
        }
        binding.recyclerView.adapter = adapter
        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            items.let { adapter.submitList(it) }
        }

        viewModel.totalSales.observe(viewLifecycleOwner) { sales ->
            binding.totalSalesText.text = getString(com.jewelrymanager.inventory.R.string.total_sales_label, String.format("$%.2f", sales))
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
