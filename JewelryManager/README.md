# Jewelry Manager 💎

A robust Android application for managing jewelry inventory with high-precision pricing, barcode scanning, and PDF reporting.

## 🚀 Features

-   **Inventory Management**: Full CRUD operations for jewelry items including categories, metals, carats, and locations.
-   **Barcode Scanner**: Integrated **CameraX** and **Google ML Kit** for rapid item lookup and data entry.
-   **Atomic Upserts**: Intelligent database handling where scanning an existing SKU automatically updates stock instead of creating duplicates.
-   **High-Precision Pricing**: Uses `BigDecimal` for all financial calculations to ensure currency integrity.
-   **Inventory Movements**: Tracks "Entry" (stock in) and "Exit" (sales) transactions for every SKU.
-   **Sales Reporting**: Real-time calculation of total sales value displayed on the dashboard and included in reports.
-   **Granular PDF Export**: Choose between a "Full Report" (Inventory + Transactions) or a focused "Sales Report" (Exit transactions only).
-   **System Notifications**: Receive a system notification upon export completion, allowing you to open the PDF directly from the status bar.
-   **Date-Based Sorting**: Tracks when items are added to the inventory, allowing sorting by "Newest First" and "Oldest First".
-   **PDF Export**: Professional inventory reports including current stock, movement history, and total sales summary.
-   **Reactive Search & Filter**: Real-time filtering by category, stock level, and name/SKU.
-   **Speed Dial FAB**: Modern, expandable Floating Action Button for quick access to core functions.

## 🛠 Tech Stack

-   **Language**: Kotlin
-   **Architecture**: MVVM (Model-View-ViewModel)
-   **Database**: Room Persistence Library
-   **Concurrency**: Kotlin Coroutines & Flow
-   **Jetpack Components**: Navigation, LiveData, ViewModel, ViewBinding
-   **Camera**: CameraX
-   **ML**: Google ML Kit Barcode Scanning
-   **Image Loading**: Coil
-   **UI**: Material Design 3

## 🏗 Data & Security Boundaries

### 1. Data Integrity
-   **SKU Primary Key**: The `sku` (Stock Keeping Unit) acts as the unique identifier and `@PrimaryKey` in the Room database, preventing logical duplicates.
-   **Currency Safety**: Financial data is stored as `String` in the database via `TypeConverters` but processed as `BigDecimal` in memory to avoid floating-point errors.

### 2. Memory-Leak Guards
-   **Lifecycle Management**: All observers use `viewLifecycleOwner` to prevent leaks during fragment transitions.
-   **Binding Cleanup**: `ViewBinding` is nullified in `onDestroyView()` to release the view hierarchy.
-   **Executor Handling**: Camera executors are explicitly shut down on fragment destruction.

### 3. Thread Safety
-   **Background Processing**: All I/O operations (Room, File Writing, PDF Generation) are offloaded to `Dispatchers.IO` and `Dispatchers.Default` using `lifecycleScope` and `viewModelScope`.
-   **Main Thread Protection**: The UI thread is never blocked, even during heavy PDF generation or large database queries.

## 📦 Installation

1.  Clone the repository.
2.  Open in **Android Studio Ladybug (or newer)**.
3.  Sync Gradle dependencies.
4.  Ensure a physical device or emulator with camera support is connected for barcode scanning.

## 📄 License

This project is licensed under the MIT License.
