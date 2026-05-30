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
import android.widget.LinearLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    private var currentItemsList: List<JewelryItem> = emptyList()
    private var currentTransactionsList: List<Transaction> = emptyList()

    private lateinit var inventoryAdapter: InventoryAdapter
    private var isFabExpanded = false

    private val requestNotificationPermissionLauncher = registerResultLauncher()

    private fun registerResultLauncher() = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInventoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setupRecyclerView()

        viewModel.allItems.observe(viewLifecycleOwner) { items ->
            currentItemsList = items ?: emptyList()
            inventoryAdapter.submitList(currentItemsList) {
                binding.recyclerView.scrollToPosition(0)
            }
        }

        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            currentTransactionsList = transactions ?: emptyList()

            val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
            val salesThisMonth = currentTransactionsList.filter { tx ->
                tx.type == TransactionType.EXIT &&
                        java.util.Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                            .get(java.util.Calendar.MONTH) == currentMonth
            }

            val topSellingSku = salesThisMonth
                .groupBy { it.sku }
                .maxByOrNull { group -> group.value.sumOf { it.quantity } }?.key

            inventoryAdapter.setMostSoldItemSku(topSellingSku)
        }

        setupSearch()
        setupFilters()
        setupFabMenu()

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>("scanned_barcode")
            ?.observe(viewLifecycleOwner) { barcode ->
                binding.searchEditText.setText(barcode)
                Toast.makeText(requireContext(), "Found barcode: $barcode", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter { item ->
            val action = InventoryListFragmentDirections
                .actionInventoryListFragmentToItemDetailFragment(item.sku)
            findNavController().navigate(action)
        }
        binding.recyclerView.adapter = inventoryAdapter

        viewModel.totalSales.observe(viewLifecycleOwner) { sales ->
            binding.totalSalesText.text = getString(
                com.jewelrymanager.inventory.R.string.total_sales_label,
                String.format("$%.2f", sales ?: BigDecimal.ZERO)
            )
        }
    }

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
            val totalSales = viewModel.totalSales.value ?: BigDecimal.ZERO

            if (currentItemsList.isEmpty() && currentTransactionsList.isEmpty()) {
                Toast.makeText(requireContext(), "No data available to export", Toast.LENGTH_SHORT).show()
            } else {
                exportToPdf(currentItemsList, currentTransactionsList, totalSales, "Full_Inventory")
            }
        }

        binding.exportSalesFabSmall.setOnClickListener {
            toggleFabMenu()
            openCustomReportDialog()
        }
    }

    private fun openCustomReportDialog() {
        val context = requireContext()
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val tvTypeLabel = android.widget.TextView(context).apply {
            text = "Select Report Type:"
            textSize = 14f
            setPadding(0, 0, 0, 10)
        }
        mainContainer.addView(tvTypeLabel)

        val reportOptions = arrayOf("Sales Report (EXIT) 📄", "Purchase Report (ENTRY) 📦")
        var selectedReportTypeIndex = 0


        val btnSelectDateRange = com.google.android.material.button.MaterialButton(context).apply {
            text = "Select Date Range 📅"
            icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_my_calendar)

            setBackgroundColor(Color.TRANSPARENT) 
            strokeWidth = 3 
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#6750A4")) 

            setTextColor(Color.parseColor("#6750A4"))
            iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#6750A4"))

            cornerRadius = 20

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }

        val tvSelectedDates = android.widget.TextView(context).apply {
            text = "All Time (No Date Filter)"
            textSize = 13f
            setPadding(10, 10, 0, 20)
            setTextColor(Color.GRAY)
        }

        mainContainer.addView(btnSelectDateRange)
        mainContainer.addView(tvSelectedDates)

        var startTimestamp: Long? = null
        var endTimestamp: Long? = null

        btnSelectDateRange.setOnClickListener {
            val dateRangePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Report Period")
                .build()

            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                startTimestamp = selection.first
                endTimestamp = selection.second?.plus(86399000L)

                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val startDateStr = sdf.format(Date(startTimestamp!!))
                val endDateStr = sdf.format(Date(endTimestamp!!))
                tvSelectedDates.text = "Period: $startDateStr to $endDateStr"
            }
            dateRangePicker.show(childFragmentManager, "DATE_RANGE_PICKER")
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Export Custom Report 📊")
            .setView(mainContainer)
            .setSingleChoiceItems(reportOptions, 0) { _, which ->
                selectedReportTypeIndex = which
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Export PDF") { dialog, _ ->
                val targetType = if (selectedReportTypeIndex == 0) TransactionType.EXIT else TransactionType.ENTRY
                val reportName = if (selectedReportTypeIndex == 0) "Sales_Report" else "Purchase_Report"

                val filteredTransactions = currentTransactionsList.filter { tx ->
                    val matchesType = tx.type == targetType
                    val matchesDate = if (startTimestamp != null && endTimestamp != null) {
                        tx.timestamp in startTimestamp!!..endTimestamp!!
                    } else {
                        true
                    }
                    matchesType && matchesDate
                }

                if (filteredTransactions.isEmpty()) {
                    Toast.makeText(context, "No transactions found for the selected criteria", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                var totalCalculatedAmount = BigDecimal.ZERO
                for (tx in filteredTransactions) {
                    val itemTotal = tx.priceAtTime.multiply(BigDecimal.valueOf(tx.quantity.toLong()))
                    totalCalculatedAmount = totalCalculatedAmount.add(itemTotal)
                }

                exportToPdf(emptyList(), filteredTransactions, totalCalculatedAmount, reportName)
                dialog.dismiss()
            }
            .show()
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

    private fun exportToPdf(
        items: List<JewelryItem>,
        transactions: List<Transaction>,
        totalSales: BigDecimal,
        reportNamePrefix: String,
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint()
            val metaPaint = Paint()

            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

            val todayDate = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val sharedPref = requireContext().getSharedPreferences("ReportPrefs", android.content.Context.MODE_PRIVATE)
            val currentCount = sharedPref.getInt("${reportNamePrefix}_count", 0) + 1
            sharedPref.edit().putInt("${reportNamePrefix}_count", currentCount).apply()

            val metaTextDate = "Date: $todayDate"
            val metaTextCount = "Download Count: #$currentCount"

            var currentPage: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var currentY = 50f

            fun startNewPdfPage(title: String?) {
                currentPage?.let { pdfDocument.finishPage(it) }

                val newPage = pdfDocument.startPage(pageInfo)
                currentPage = newPage
                canvas = newPage.canvas
                currentY = 50f

                metaPaint.textSize = 10f
                metaPaint.color = android.graphics.Color.GRAY
                metaPaint.textAlign = Paint.Align.RIGHT
                canvas?.drawText(metaTextDate, 550f, 30f, metaPaint)
                canvas?.drawText(metaTextCount, 550f, 45f, metaPaint)

                title?.let {
                    titlePaint.textSize = 18f
                    titlePaint.isFakeBoldText = true
                    titlePaint.textAlign = Paint.Align.CENTER
                    canvas?.drawText(it, (pageWidth / 2).toFloat(), 70f, titlePaint)
                    currentY = 110f
                }
            }

            if (items.isNotEmpty()) {
                startNewPdfPage("Inventory Report")

                fun drawInventoryHeaders(targetCanvas: Canvas, y: Float) {
                    paint.isFakeBoldText = true
                    targetCanvas.drawText("SKU", 50f, y, paint)
                    targetCanvas.drawText("Name", 150f, y, paint)
                    targetCanvas.drawText("Category", 350f, y, paint)
                    targetCanvas.drawText("Qty", 450f, y, paint)
                    targetCanvas.drawText("Price", 500f, y, paint)
                    targetCanvas.drawLine(50f, y + 5f, 550f, y + 5f, paint)
                    paint.isFakeBoldText = false
                }

                canvas?.let { drawInventoryHeaders(it, currentY) }
                currentY += 30f

                for (item in items) {
                    if (currentY > 750) {
                        startNewPdfPage(null)
                        canvas?.let { drawInventoryHeaders(it, currentY) }
                        currentY += 30f
                    }

                    canvas?.drawText(item.sku, 50f, currentY, paint)
                    val displayName = if (item.name.length > 22) item.name.substring(0, 19) + "..." else item.name
                    canvas?.drawText(displayName, 150f, currentY, paint)
                    canvas?.drawText(item.category, 350f, currentY, paint)
                    canvas?.drawText(item.quantity.toString(), 450f, currentY, paint)
                    canvas?.drawText(String.format("$%.2f", item.retailPrice), 500f, currentY, paint)

                    currentY += 25f
                }
            }

            if (transactions.isNotEmpty()) {
                val sectionTitle = if (items.isEmpty()) {
                    if (reportNamePrefix.contains("Sales")) "Sales Report (EXIT)" else "Purchase Report (ENTRY)"
                } else {
                    "Inventory Movements"
                }

                startNewPdfPage(sectionTitle)

                paint.isFakeBoldText = true
                paint.textSize = 12f
                canvas?.drawText("Total Value: " + String.format("$%.2f", totalSales), 50f, currentY, paint)
                currentY += 40f

                fun drawTransactionHeaders(targetCanvas: Canvas, y: Float) {
                    paint.isFakeBoldText = true
                    targetCanvas.drawText("Date", 50f, y, paint)
                    targetCanvas.drawText("SKU", 180f, y, paint)
                    targetCanvas.drawText("Type", 300f, y, paint)
                    targetCanvas.drawText("Qty", 400f, y, paint)
                    targetCanvas.drawText("Value", 480f, y, paint)
                    targetCanvas.drawLine(50f, y + 5f, 550f, y + 5f, paint)
                    paint.isFakeBoldText = false
                }

                canvas?.let { drawTransactionHeaders(it, currentY) }
                currentY += 30f

                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

                for (tx in transactions) {
                    if (currentY > 750) {
                        startNewPdfPage(null)
                        canvas?.let { drawTransactionHeaders(it, currentY) }
                        currentY += 30f
                    }

                    canvas?.drawText(dateFormat.format(Date(tx.timestamp)), 50f, currentY, paint)
                    canvas?.drawText(tx.sku, 180f, currentY, paint)
                    canvas?.drawText(tx.type.name, 300f, currentY, paint)
                    canvas?.drawText(tx.quantity.toString(), 400f, currentY, paint)
                    val txValue = tx.priceAtTime.multiply(BigDecimal(tx.quantity))
                    canvas?.drawText(String.format("$%.2f", txValue), 480f, currentY, paint)

                    currentY += 25f
                }
            }

            currentPage?.let { pdfDocument.finishPage(it) }

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
                        uri?.let { targetUri ->
                            requireContext().contentResolver.openOutputStream(targetUri).use { outputStream ->
                                pdfDocument.writeTo(outputStream)
                            }
                            withContext(Dispatchers.Main) {
                                NotificationHelper.showExportNotification(requireContext(), targetUri, fileName)
                            }
                        }
                    } else {
                        val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        FileOutputStream(filePath).use { outputStream -> pdfDocument.writeTo(outputStream) }
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
            } finally {
                pdfDocument.close()
            }
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