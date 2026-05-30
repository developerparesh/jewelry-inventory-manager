package com.jewelrymanager.inventory.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.jewelrymanager.inventory.JewelryApplication
import com.jewelrymanager.inventory.R
import com.jewelrymanager.inventory.data.TransactionType
import com.jewelrymanager.inventory.databinding.FragmentBarcodeScanBinding
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScanFragment : Fragment() {

    private val viewModel: InventoryViewModel by activityViewModels {
        InventoryViewModelFactory(
            (requireActivity().application as JewelryApplication).repository
        )
    }

    private var _binding: FragmentBarcodeScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private val scannedItemsMap = mutableMapOf<String, Int>()
    private var lastScannedBarcode: String? = null
    private var lastScanTimestamp: Long = 0L


    private var isDialogActive = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBarcodeScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setupUIButtons()
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        onBarcodeDetected(barcode)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("BarcodeScanFragment", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun onBarcodeDetected(barcode: String) {
        if (isDialogActive) {
            return
        }
        val currentTime = System.currentTimeMillis()

        if (barcode == lastScannedBarcode && (currentTime - lastScanTimestamp) < 1500) {
            return
        }

        lastScannedBarcode = barcode
        lastScanTimestamp = currentTime

        val currentQty = scannedItemsMap[barcode] ?: 0
        scannedItemsMap[barcode] = currentQty + 1

        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                "Scanned: $barcode (Qty: ${scannedItemsMap[barcode]})",
                Toast.LENGTH_SHORT
            ).show()
            updateScanCountUI()
        }
    }

    private fun updateScanCountUI() {
        val totalQuantity = scannedItemsMap.values.sum()
        binding.btnSubmitEntry.text = "Save as ENTRY ($totalQuantity Items)"
        binding.btnSubmitExit.text = "Save as EXIT ($totalQuantity Items)"
    }

    private fun setupUIButtons() {
        binding.btnSubmitEntry.setOnClickListener {
            saveAllTransactions(TransactionType.ENTRY)
        }

        binding.btnSubmitExit.setOnClickListener {
            saveAllTransactions(TransactionType.EXIT)
        }
    }

    private fun saveAllTransactions(type: TransactionType) {
        if (scannedItemsMap.isEmpty()) {
            Toast.makeText(requireContext(), "No items scanned yet", Toast.LENGTH_SHORT).show()
            return
        }

        val allItemsInDb = viewModel.allItems.value ?: emptyList()
        val errorMessageBuilder = StringBuilder()
        val unknownItems = mutableListOf<String>()

        for ((barcode, scannedQty) in scannedItemsMap) {
            val dbItem = allItemsInDb.find { it.sku == barcode }

            if (dbItem == null) {
                unknownItems.add(barcode)
                errorMessageBuilder.append("Item $barcode does not exist in inventory!\n")
            } else if (type == TransactionType.EXIT && dbItem.quantity < scannedQty) {
                errorMessageBuilder.append("Shortage for ${dbItem.name} ($barcode): Scanned $scannedQty, but only ${dbItem.quantity} available!\n")
            }
        }

        if (errorMessageBuilder.isNotEmpty()) {
            isDialogActive = true
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Validation Error ❌")
                .setMessage(errorMessageBuilder.toString().trim())
                .setPositiveButton("Fix Scanning") { dialog, _ ->
                    isDialogActive = false
                    dialog.dismiss()
                }
                .setNegativeButton("Clear All") { dialog, _ ->
                    scannedItemsMap.clear()
                    updateScanCountUI()
                    isDialogActive = false
                    dialog.dismiss()
                }
                .setOnCancelListener {
                    isDialogActive = false
                }
                .show()
            return
        }

        if (type == TransactionType.EXIT) {
            showPaymentAndDetailsDialog(allItemsInDb)
        } else {
            val context = requireContext()
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 20, 40, 20)
            }

            val tilName = TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Vendor / Supplier Name"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
            val etName = com.google.android.material.textfield.MaterialAutoCompleteTextView(context)
                .apply { threshold = 3 }
            tilName.addView(etName)

            val tilPhone = TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Vendor Phone Number"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
            val etPhone = TextInputEditText(context).apply {
                inputType = android.text.InputType.TYPE_CLASS_PHONE
            }
            tilPhone.addView(etPhone)

            container.addView(tilName)
            container.addView(tilPhone)

            tilPhone.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 20, 0, 20)
            }

            viewModel.allPastPartners.observe(viewLifecycleOwner) { partnerList ->
                val adapter = android.widget.ArrayAdapter(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    partnerList ?: emptyList()
                )
                etName.setAdapter(adapter)
            }

            etName.setOnItemClickListener { parent, _, position, _ ->
                val selectedItem = parent.getItemAtPosition(position) as String
                val name = selectedItem.substringBefore(" (").trim()
                val phone = selectedItem.substringAfter("(").substringBefore(")").trim()
                etName.setText(name, false)
                etPhone.setText(phone)
            }
            isDialogActive = true
            MaterialAlertDialogBuilder(context)
                .setTitle("Receive Goods / Entry 📦")
                .setMessage("Enter Vendor details to record stock arrival.")
                .setView(container)
                .setCancelable(false)
                .setNegativeButton("Cancel"){ dialog, _ ->
                    isDialogActive = false
                    dialog.dismiss()
                }
                .setPositiveButton("Save Stock Entry") { dialog, _ ->
                    val vendorName = etName.text?.toString()?.trim()?.ifEmpty { "Regular Vendor" }
                        ?: "Regular Vendor"
                    val vendorPhone = etPhone.text?.toString()?.trim()?.ifEmpty { "N/A" } ?: "N/A"

                    val textSummaryForVendor = StringBuilder()
                    var totalReceivedItems = 0

                    for ((barcode, qty) in scannedItemsMap) {
                        viewModel.performPurchaseTransaction(barcode, qty, vendorName, vendorPhone)

                        val dbItem = allItemsInDb.find { it.sku == barcode }
                        val itemName = dbItem?.name ?: "Unknown Product"
                        textSummaryForVendor.append("• $itemName ($barcode) - Qty: $qty\n")
                        totalReceivedItems += qty
                    }

                    Toast.makeText(requireContext(), "All entries recorded!", Toast.LENGTH_LONG)
                        .show()

                    if (vendorPhone != "N/A" && vendorPhone.length >= 10) {
                        openShareVendorReceiptDialog(
                            vendorName,
                            vendorPhone,
                            totalReceivedItems,
                            textSummaryForVendor.toString()
                        )
                    }
                    isDialogActive = false
                    scannedItemsMap.clear()
                    updateScanCountUI()
                    dialog.dismiss()
                }
                .show()
        }

    }

    private fun showPaymentAndDetailsDialog(allItemsInDb: List<com.jewelrymanager.inventory.data.JewelryItem>) {
        val context = requireContext()

        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }

        val tilName =
            TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Customer Name"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE 
                setBoxCornerRadii(12f, 12f, 12f, 12f) 
            }
        val etName =
            com.google.android.material.textfield.MaterialAutoCompleteTextView(context).apply {
                threshold = 3
            }
        tilName.addView(etName)

        val tilPhone =
            TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Customer Phone"
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
        val etPhone = TextInputEditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        tilPhone.addView(etPhone)

        mainContainer.addView(tilName)
        mainContainer.addView(tilPhone)

        viewModel.allPastPartners.observe(viewLifecycleOwner) { partnerList ->
            val adapter = android.widget.ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                partnerList ?: emptyList()
            )
            etName.setAdapter(adapter)
        }

        etName.setOnItemClickListener { parent, _, position, _ ->
            val selectedItem = parent.getItemAtPosition(position) as String
            val name = selectedItem.substringBefore(" (").trim()
            val phone = selectedItem.substringAfter("(").substringBefore(")").trim()
            etName.setText(name, false)
            etPhone.setText(phone)
        }

        var grandTotal = BigDecimal.ZERO
        val summaryBuilder = StringBuilder()
        for ((barcode, qty) in scannedItemsMap) {
            val dbItem = allItemsInDb.find { it.sku == barcode }
            val price = dbItem?.retailPrice ?: BigDecimal.ZERO
            val itemTotal = price.multiply(BigDecimal.valueOf(qty.toLong()))
            grandTotal = grandTotal.add(itemTotal)
            summaryBuilder.append(
                "• ${dbItem?.name ?: "Unknown"} (x$qty) - $${
                    String.format(
                        "%.2f",
                        itemTotal
                    )
                }\n"
            )
        }

        val tvLabel = android.widget.TextView(context).apply {
            text = "Select Payment Mode(s) & Enter Amount:"
            textSize = 14f
            setPadding(0, 10, 0, 10)
        }
        mainContainer.addView(tvLabel)

        val cashLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cbCash =
            com.google.android.material.checkbox.MaterialCheckBox(context).apply { text = "Cash  " }
        val tilCashAmt =
            TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Cash Amount"
                visibility = View.GONE
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
        val etCashAmt = TextInputEditText(context).apply {
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilCashAmt.addView(etCashAmt)
        cashLayout.addView(cbCash)
        cashLayout.addView(tilCashAmt)
        mainContainer.addView(cashLayout)

        val onlineLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(
            0,
            10,
            0,
            10
        )
        }
        val cbOnline =
            com.google.android.material.checkbox.MaterialCheckBox(context).apply { text = "Online" }
        val tilOnlineAmt =
            TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Online Amount"
                visibility = View.GONE
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
        val etOnlineAmt = TextInputEditText(context).apply {
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilOnlineAmt.addView(etOnlineAmt)
        onlineLayout.addView(cbOnline)
        onlineLayout.addView(tilOnlineAmt)
        mainContainer.addView(onlineLayout)

        val cardLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val cbCard =
            com.google.android.material.checkbox.MaterialCheckBox(context)
                .apply { text = "Card    " }
        val tilCardAmt =
            TextInputLayout(
                context,
                null,
                com.google.android.material.R.attr.textInputStyle
            ).apply {
                hint = "Card Amount"
                visibility = View.GONE
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(12f, 12f, 12f, 12f)
            }
        val etCardAmt = TextInputEditText(context).apply {
            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        tilCardAmt.addView(etCardAmt)
        cardLayout.addView(cbCard)
        cardLayout.addView(tilCardAmt)
        mainContainer.addView(cardLayout)

        cbCash.setOnCheckedChangeListener { _, isChecked ->
            tilCashAmt.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) etCashAmt.setText(grandTotal.toPlainString()) else etCashAmt.setText("")
        }
        cbOnline.setOnCheckedChangeListener { _, isChecked ->
            tilOnlineAmt.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && !cbCash.isChecked) etOnlineAmt.setText(grandTotal.toPlainString()) else etOnlineAmt.setText(
                ""
            )
        }
        cbCard.setOnCheckedChangeListener { _, isChecked ->
            tilCardAmt.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && !cbCash.isChecked && !cbOnline.isChecked) etCardAmt.setText(grandTotal.toPlainString()) else etCardAmt.setText(
                ""
            )
        }
        isDialogActive = true
        MaterialAlertDialogBuilder(context)
            .setTitle("Complete Sale 📄")
            .setView(mainContainer)
            .setCancelable(false)
            .setMessage(
                "Items Summary:\n$summaryBuilder\nGrand Total: $${
                    String.format(
                        "%.2f",
                        grandTotal
                    )
                }\n"
            )
            .setNegativeButton("Cancel") { dialog, _ ->
                isDialogActive = false
                dialog.dismiss()
            }
            .setPositiveButton("Generate Bill") { dialog, _ ->

                val cashPaid =
                    if (cbCash.isChecked) etCashAmt.text?.toString()?.toBigDecimalOrNull()
                        ?: BigDecimal.ZERO else BigDecimal.ZERO
                val onlinePaid =
                    if (cbOnline.isChecked) etOnlineAmt.text?.toString()?.toBigDecimalOrNull()
                        ?: BigDecimal.ZERO else BigDecimal.ZERO
                val cardPaid =
                    if (cbCard.isChecked) etCardAmt.text?.toString()?.toBigDecimalOrNull()
                        ?: BigDecimal.ZERO else BigDecimal.ZERO

                val totalPaid = cashPaid.add(onlinePaid).add(cardPaid)

                if (totalPaid == BigDecimal.ZERO) {
                    Toast.makeText(
                        context,
                        "Please select at least one payment method!",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                val paymentList = mutableListOf<String>()
                if (cashPaid > BigDecimal.ZERO) paymentList.add(
                    "Cash ($${
                        String.format(
                            "%.2f",
                            cashPaid
                        )
                    })"
                )
                if (onlinePaid > BigDecimal.ZERO) paymentList.add(
                    "Online ($${
                        String.format(
                            "%.2f",
                            onlinePaid
                        )
                    })"
                )
                if (cardPaid > BigDecimal.ZERO) paymentList.add(
                    "Card ($${
                        String.format(
                            "%.2f",
                            cardPaid
                        )
                    })"
                )
                val finalPaymentModeText = paymentList.joinToString(", ")

                val customerName = etName.text?.toString()?.trim()?.ifEmpty { "Walk-in Customer" }
                    ?: "Walk-in Customer"
                val customerPhone = etPhone.text?.toString()?.trim()?.ifEmpty { "N/A" } ?: "N/A"

                val itemsCopyForPdf = HashMap(scannedItemsMap)

                for ((barcode, qty) in scannedItemsMap) {
                    viewModel.performSaleTransaction(barcode, qty, customerName, customerPhone)
                }
                isDialogActive = false
                generateSingleCustomerBill(
                    customerName,
                    customerPhone,
                    grandTotal,
                    finalPaymentModeText,
                    itemsCopyForPdf
                )

                scannedItemsMap.clear()
                updateScanCountUI()
                dialog.dismiss()
            }
            .show()
    }

    private fun generateSingleCustomerBill(
        customerName: String,
        customerPhone: String,
        grandTotal: BigDecimal,
        paymentMode: String,
        itemsBillMap: Map<String, Int>,
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint()

            val pageWidth = 595
            val pageHeight = 842
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val todayDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

            titlePaint.textSize = 24f
            titlePaint.isFakeBoldText = true
            titlePaint.textAlign = Paint.Align.CENTER
            canvas.drawText("JEWELRY MANAGEMENT SHOP", (pageWidth / 2).toFloat(), 60f, titlePaint)

            titlePaint.textSize = 14f
            titlePaint.isFakeBoldText = false
            canvas.drawText("Tax Invoice / Retail Bill", (pageWidth / 2).toFloat(), 85f, titlePaint)

            paint.textSize = 12f
            canvas.drawText("Date: $todayDate", 50f, 130f, paint)
            canvas.drawText("Customer Name: $customerName", 50f, 150f, paint)
            canvas.drawText("Customer Phone: $customerPhone", 50f, 170f, paint)
            canvas.drawLine(50f, 185f, 545f, 185f, paint)

            paint.isFakeBoldText = true
            canvas.drawText("Item Details", 50f, 205f, paint)
            canvas.drawText("Qty", 380f, 205f, paint)
            canvas.drawText("Total Price", 470f, 205f, paint)
            canvas.drawLine(50f, 215f, 545f, 215f, paint)
            paint.isFakeBoldText = false

            var currentY = 240f
            val allItemsInDb = viewModel.allItems.value ?: emptyList()
            val textSummaryForSharing = StringBuilder()

            var realCalculatedTotal = BigDecimal.ZERO

            for ((barcode, qty) in itemsBillMap) {
                val dbItem = allItemsInDb.find { it.sku == barcode }

                val itemName = dbItem?.name ?: "Unregistered Item"
                val price = dbItem?.retailPrice ?: BigDecimal.ZERO
                val itemTotal = price.multiply(BigDecimal.valueOf(qty.toLong()))

                realCalculatedTotal = realCalculatedTotal.add(itemTotal)

                canvas.drawText("$itemName ($barcode)", 50f, currentY, paint)
                canvas.drawText(qty.toString(), 380f, currentY, paint)
                canvas.drawText(String.format("$%.2f", itemTotal), 470f, currentY, paint)

                textSummaryForSharing.append(
                    "• $itemName (x$qty) - $${
                        String.format(
                            "%.2f",
                            itemTotal
                        )
                    }\n"
                )
                currentY += 25f
            }

            canvas.drawLine(50f, currentY, 545f, currentY, paint)
            currentY += 30f

            paint.isFakeBoldText = true
            paint.textSize = 12f
            canvas.drawText("Payment Mode: $paymentMode", 50f, currentY, paint)

            paint.textSize = 14f
            canvas.drawText("Grand Total Amount:", 230f, currentY, paint)
            canvas.drawText(String.format("$%.2f", realCalculatedTotal), 470f, currentY, paint)

            currentY += 50f
            paint.textSize = 11f
            paint.isFakeBoldText = false
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                "Thank you for shopping with us! Visit Again.",
                (pageWidth / 2).toFloat(),
                currentY,
                paint
            )

            pdfDocument.finishPage(page)

            val fileTimestamp =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Bill_${customerName.replace(" ", "_")}_$fileTimestamp.pdf"

            try {
                withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS
                            )
                        }
                        val uri = requireContext().contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        uri?.let { targetUri ->
                            requireContext().contentResolver.openOutputStream(targetUri)
                                .use { outputStream ->
                                    pdfDocument.writeTo(outputStream)
                                }
                        }
                    } else {
                        val filePath = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            fileName
                        )
                        FileOutputStream(filePath).use { outputStream ->
                            pdfDocument.writeTo(
                                outputStream
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Invoice Bill Downloaded successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    if (customerPhone != "N/A" && customerPhone.length >= 10) {
                        val fullMessage = """
                        *JEWELRY MANAGEMENT SHOP* 📄
                        ------------------------------------
                        👤 *Customer:* $customerName
                        📞 *Phone:* $customerPhone
                        💳 *Payment:* $paymentMode
                        ------------------------------------
                        *Items List:*
                        $textSummaryForSharing
                        ------------------------------------
                        💰 *Grand Total:* $${String.format("%.2f", realCalculatedTotal)}
                        
                        Thank you for shopping with us! Visit Again.
                    """.trimIndent()

                        shareToWhatsApp(customerPhone, fullMessage)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                pdfDocument.close()
            }
        }
    }

    private fun openShareInvoiceDialog(
        name: String,
        phone: String,
        total: BigDecimal,
        paymentMode: String,
        itemsText: String,
    ) {
        val fullMessage = """
            *JEWELRY MANAGEMENT SHOP* 📄
            ------------------------------------
            👤 *Customer:* $name
            📞 *Phone:* $phone
            💳 *Payment Mode:* $paymentMode
            ------------------------------------
            *Items List:*
            $itemsText
            ------------------------------------
            <b>💰 *Grand Total:* $${String.format("%.2f", total)}</b>
            
            Thank you for shopping with us! Visit Again.
        """.trimIndent()

        val shareOptions = arrayOf("Send via WhatsApp 🟢", "Send via SMS 💬")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Share Invoice / Bill 📲")
            .setItems(shareOptions) { dialog, which ->
                when (which) {
                    0 -> shareToWhatsApp(phone, fullMessage)
                    1 -> shareToSMS(phone, fullMessage)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun openShareVendorReceiptDialog(
        vendorName: String,
        vendorPhone: String,
        totalQty: Int,
        itemsText: String,
    ) {
        val todayDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault()).format(Date())

        val fullVendorMessage = """
            *GOODS RECEIVED CONFIRMATION* 📦
            ------------------------------------
            🏪 *To Store/Vendor:* $vendorName
            📞 *Vendor Phone:* $vendorPhone
            📅 *Received Date:* $todayDate
            ------------------------------------
            *We have successfully received following items:*
            $itemsText
            ------------------------------------
            📦 *Total Received Items:* $totalQty
            
            Thank you for fulfilling our order!
        """.trimIndent()

        val shareOptions = arrayOf("Send via WhatsApp 🟢", "Send via SMS 💬")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Receipt to Vendor 📲")
            .setMessage("Do you want to send a stock confirmation message to $vendorName?")
            .setItems(shareOptions) { dialog, which ->
                when (which) {
                    0 -> shareToWhatsApp(vendorPhone, fullVendorMessage)
                    1 -> shareToSMS(vendorPhone, fullVendorMessage)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun shareToWhatsApp(phone: String, message: String) {
        val formattedPhone =
            if (!phone.startsWith("91") && phone.length == 10) "91$phone" else phone
        try {
            val uri =
                Uri.parse(
                    "https://api.whatsapp.com/send?phone=$formattedPhone&text=${
                        Uri.encode(
                            message
                        )
                    }"
                )
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "WhatsApp is not installed!", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun shareToSMS(phone: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", message)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open SMS application", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) :
        ImageAnalysis.Analyzer {
        private val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        private val scanner = BarcodeScanning.getClient(options)

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let {
                                onBarcodeDetected(it)
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("BarcodeAnalyzer", "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}