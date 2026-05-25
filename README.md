# jewelry-inventory-manager
A robust Android MVP for multi-location retail jewelry stock and inventory management, built using Kotlin, MVVM architecture, Room Database, and a modern single-tab Master-Detail UI layout

## 🚀 Features Matrix

### Core MVP (100% Completed & Functional)
- [x] **Single-Screen Architecture:** Unified the Inventory List, Detailed Properties View, and Add/Edit entry state into a single-activity layout via dynamic panel state adjustments to avoid fragment inflation overhead.
- [x] **Reactive Storage:** Full offline persistence using Room Database pre-loaded with a comprehensive 120-item testing matrix.
- [x] **Real-Time Indexing:** Live text query tracking across Name and SKU properties.
- [x] **Categorization Engine:** Functional filter matrices driven by a native Spinner component.

### Future Roadmap (Planned Enhancements)
- [ ] **Automated Data Capture:** Lifecycle-aware Barcode and QR scanning integration via CameraX and localized Google ML Kit blocks.
- [ ] **In-Memory CSV Export:** Dynamic text matrix flattening paired with Android Implicit Intents for immediate corporate data distribution.
