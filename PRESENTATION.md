# ETL-TTU — Panduan Presentasi

> Baca ini sebelum presentasi. Berisi poin-poin penting, penjelasan teknis, dan urutan demo.

---

## Gambaran Besar

**ETL-TTU** adalah sistem backend yang mengotomatisasi proses pemindahan data penjualan dari file Excel (.xlsx) ke database PostgreSQL. Singkatan **ETL** = **Extract → Transform → Load**.

**Stack teknologi:**
- Java 21 + Spring Boot 3
- PostgreSQL 17
- Flyway (database migration)
- Apache POI + StreamingReader (baca Excel)
- JPA/Hibernate (ORM)

---

## Point 1 — Arsitektur Pipeline ETL

### Alur utama

```
User upload .xlsx
      ↓
  [PENDING]
      ↓
  [EXTRACTING]  →  Baca & parse Excel row by row
      ↓
  [TRANSFORMING] →  Validasi & konversi tipe data
      ↓
  [LOADING]     →  Batch insert ke PostgreSQL
      ↓
  [COMPLETED]   →  Stats tersimpan, data siap diquery
```

### Yang perlu ditonjolkan

- **Non-blocking upload** — user upload file, langsung dapat response berisi `jobId`. Proses berjalan di background (`@Async`). User tidak perlu menunggu.
- **Status real-time** — setiap job punya status yang bisa dipantau kapan saja via `GET /api/etl/jobs/{jobId}`
- **Stats per fase** — setiap tahap mencatat berapa record yang berhasil/gagal:

| Field | Artinya |
|---|---|
| `extractedRecords` | Berapa receipt terbaca dari Excel |
| `validRecords` | Berapa yang lolos validasi |
| `failedRecords` | Berapa yang gagal validasi |
| `duplicateRecords` | Berapa yang sudah ada di DB (dilewati) |
| `loadedRecords` | Berapa yang berhasil masuk ke DB |
| `durationSeconds` | Berapa detik total proses |

---

## Point 2 — Smart Excel Parser (Teknis Paling Menarik)

File: `ExcelExtractorService.java`

### Masalah yang dipecahkan

File Excel dari sistem POS tidak selalu konsisten — posisi kolom bisa bergeser tergantung template. Daripada hardcode posisi kolom, sistem **mendeteksi layout secara otomatis**.

### Cara kerjanya

1. Sistem scan baris demi baris mencari **baris header tabel** (baris yang mengandung keyword seperti "No.", "Nama Item", "Satuan", "Total", dll.)
2. Setelah ketemu, sistem **catat posisi kolom** masing-masing field secara otomatis
3. Baru setelah itu data item dibaca berdasarkan posisi yang terdeteksi

```java
// Deteksi otomatis — tidak hardcode angka kolom
if (lower.contains("nama") || lower.contains("name")) layout.itemName = col;
if (lower.contains("qty") || lower.contains("jml"))   layout.qty = col;
if (lower.contains("total"))                           layout.total = col;
```

### Fitur lain parser

- **Streaming** — pakai `StreamingReader`, file dibaca row by row, **tidak di-load seluruhnya ke memory**. Bisa handle file hingga 50MB tanpa OutOfMemory.
- **Multi-receipt dalam 1 file** — 1 file Excel bisa berisi ratusan transaksi. Parser otomatis mendeteksi batas antar transaksi (header row baru) dan memisahkannya.
- **Toleran format tanggal** — tanggal yang berbeda format tetap dicoba diparse sebelum dianggap error.

---

## Point 3 — Validasi & Error Traceability

File: `DataTransformerService.java`

### Apa yang divalidasi

| Field | Validasi |
|---|---|
| Tanggal transaksi | Multi-format parsing, wajib ada |
| Quantity | Parsing angka + satuan sekaligus (misal: "5 PCS") |
| Harga & Total | Parsing angka dengan format ribuan (1.000 atau 1,000) |
| Grand Total | Cross-check: `subtotal − discount + tax + fee ≈ grand_total` |

### Tipe error yang dicatat

| Error Type | Artinya |
|---|---|
| `FORMAT_ERROR` | Nilai tidak bisa diparse (tanggal salah format, angka tidak valid) |
| `VALIDATION_ERROR` | Data tidak lolos aturan bisnis |
| `DUPLICATE_ERROR` | Nomor transaksi sudah ada di database |
| `QTY_FRACTION_INFO` | Info: grand total selisih > 1 (kemungkinan pembulatan) |

### Setiap error dicatat dengan detail

```
Row Number  : 47
Field Name  : Grand Total
Error Type  : QTY_FRACTION_INFO
Description : calculated=150000 (subtotal=155000 - disc=5000), actual=149999, diff=1.00
```

Detail error bisa dilihat langsung dari response `GET /api/etl/jobs/{jobId}` — semua error inline dalam satu response tanpa endpoint tambahan.

---

## Point 4 — Performa: Batch Insert & Duplicate Detection

File: `DataLoaderService.java`

### Masalah tanpa optimasi

Kalau load 4000 transaksi tanpa optimasi:
- 4000 SELECT untuk cek duplikat → lambat
- 4000 INSERT satu per satu → sangat lambat

### Solusi yang diimplementasikan

**1. In-memory duplicate detection**
```java
// Load SEMUA nomor transaksi yang sudah ada — 1 query saja
Set<String> existingTxnNos = new HashSet<>(
    transactionHeaderRepository.findAllTransactionNos()
);

// Cek duplikat = O(1), tanpa query tambahan
if (existingTxnNos.contains(receipt.getTransactionNo())) { ... }
```

**2. Batch insert**
```java
// Kumpulkan dulu sampai 1000 record, baru saveAll sekaligus
if (batch.size() >= batchSize) {
    transactionHeaderRepository.saveAll(batch);
    batch.clear();
}
```

**3. Konfigurasi PostgreSQL**
```yaml
# di application.yml
url: jdbc:postgresql://...?reWriteBatchedInserts=true
hibernate.jdbc.batch_size: 1000
```

Hasilnya: proses ribuan transaksi dalam hitungan detik.

---

## Point 5 — Database Schema Evolution (Flyway)

Tunjukkan ini sebagai bukti project dikerjakan secara **iteratif dan terstruktur**, bukan langsung jadi.

| File | Yang dilakukan |
|---|---|
| `V1__initial_schema.sql` | Schema generik awal (products, customers, transactions) |
| `V2__normalized_schema.sql` | **Redesign total** — struktur baru: `transactions` + `transaction_items` + `products` yang lebih sesuai kebutuhan POS |
| `V3__product_traceability.sql` | Tambah relasi produk ke ETL job (lacak dari upload mana produk masuk) |
| `V4__drop_product_aliases.sql` | Hapus tabel alias yang tidak jadi dipakai (clean up) |
| `V5__analytics_indexes.sql` | Tambah composite index untuk performa query analytics |

```sql
-- V5 — contoh index yang ditambahkan untuk analytics
CREATE INDEX idx_transaction_items_item_code
    ON transaction_items (item_code);

CREATE INDEX idx_transaction_items_item_code_transaction_id
    ON transaction_items (item_code, transaction_id);
```

**Kenapa penting:** Flyway menjamin setiap environment (dev, staging, production) punya schema yang identik dan migrasi berjalan berurutan dan aman.

---

## Point 6 — Analytics API

Fitur yang paling mudah di-demo karena hasilnya langsung terlihat.

### Top Products
```
GET /api/etl/analytics/top-products
GET /api/etl/analytics/top-products?startDate=2025-01-01&endDate=2025-03-31
```
Mengembalikan produk terlaris berdasarkan total qty terjual + total revenue + jumlah transaksi.

### Sales Trend
```
GET /api/etl/analytics/trend?granularity=monthly
GET /api/etl/analytics/trend?granularity=weekly&itemCode=BRG001
GET /api/etl/analytics/trend?granularity=daily&startDate=2025-01-01&endDate=2025-01-31
```
Trend penjualan per periode (harian/mingguan/bulanan), bisa difilter per produk.

### Transaction Explorer
```
GET /api/etl/transactions/dates         → kalender: tanggal + jumlah transaksi
GET /api/etl/transactions?date=2025-01-15  → semua transaksi di tanggal tersebut
GET /api/etl/transactions/detail?no=000001/KSR/UTM/1025  → detail + semua item
```

---

## Point 7 — Export & Interoperability

Data tidak terkunci di dalam sistem — semua bisa diekspor.

| Endpoint | Output |
|---|---|
| `GET /api/etl/jobs/{id}/export?type=transactions` | CSV transaksi |
| `GET /api/etl/jobs/{id}/export?type=items` | CSV line items |
| `GET /api/etl/products?format=csv` | CSV katalog produk |

Ini penting untuk integrasi ke sistem lain (dashboard, BI tools, spreadsheet).

---

## Urutan Demo yang Disarankan

### Step 1 — Upload file
```
POST /api/etl/jobs
Body: form-data → file: [pilih file .xlsx]
```
Tunjukkan bahwa response langsung keluar (tidak nunggu proses):
```json
{
  "jobId": "1938d66d-...",
  "status": "PENDING",
  "message": "File uploaded successfully. Processing started."
}
```

### Step 2 — Monitor status
```
GET /api/etl/jobs/1938d66d-...
```
Refresh beberapa kali, tunjukkan status berubah: `EXTRACTING → TRANSFORMING → LOADING → COMPLETED`

### Step 3 — Lihat hasil akhir
```json
{
  "status": "COMPLETED",
  "extractedRecords": 150,
  "validRecords": 147,
  "failedRecords": 3,
  "duplicateRecords": 0,
  "loadedRecords": 147,
  "durationSeconds": 2
}
```

### Step 4 — Analytics
```
GET /api/etl/analytics/top-products
GET /api/etl/analytics/trend?granularity=monthly
```

### Step 5 — Export CSV
```
GET /api/etl/jobs/{id}/export?type=transactions
```

---

## Poin Penutup yang Bisa Disampaikan

- Sistem ini **production-ready**: logging terstruktur, error handling terpusat (`GlobalExceptionHandler`), konfigurasi via `application.yml`
- Mudah di-extend: kalau ada format Excel baru, cukup tambahkan keyword deteksi di `tryDetectLayout()`
- Database schema terdokumentasi dan versi-terkontrol lewat Flyway
- Semua proses berat (baca file, validasi, insert) berjalan **async** sehingga API tetap responsif

---

*File ini dibuat untuk keperluan presentasi project ETL-TTU.*
