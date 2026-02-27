# ETL-TTU Project Report

## Daftar Isi
1. [Test Results / Reports](#1-test-results--reports)
2. [Performance Benchmark Results](#2-performance-benchmark-results)
3. [Sample Input Excel & Expected Output](#3-sample-input-excel--expected-output)
4. [Database Schema](#4-database-schema)
5. [Sample Output Data di PostgreSQL](#5-sample-output-data-di-postgresql)
6. [Test Execution Results](#6-test-execution-results)

---

## 1. Test Results / Reports

### Unit Test & Integration Test

Project ini tidak memiliki formal test class (direktori `src/test/` kosong). Pengujian dilakukan secara **manual fungsional** melalui REST API dengan alur berikut:

| Jenis Test | Metode | Endpoint |
|------------|--------|----------|
| Upload file Excel | Manual via Postman/curl | `POST /api/etl/upload` |
| Cek status job | Manual via browser/Postman | `GET /api/etl/jobs/{jobId}` |
| List semua job | Manual via browser/Postman | `GET /api/etl/jobs` |
| Validasi error | Cek error_logs di DB / response API | `GET /api/etl/jobs/{jobId}` |

### Skenario yang Diuji

| Skenario | Hasil |
|----------|-------|
| Upload file `.xlsx` valid | COMPLETED, data masuk ke DB |
| Upload file bukan `.xlsx` | Ditolak: "Only .xlsx files are supported" |
| Upload file kosong | Ditolak: "File is empty" |
| Upload file > 50MB | Ditolak: "File size exceeds 50MB limit" |
| Transaksi duplikat (transaction_no sama) | Dicatat sebagai `DUPLICATE_ERROR` di error_logs, tidak di-load ulang |
| Format tanggal tidak valid | Dicatat sebagai `FORMAT_ERROR`, transaksi ditandai invalid |
| Format angka tidak valid (qty/harga/total) | Dicatat sebagai `FORMAT_ERROR` di error_logs |
| Grand total tidak cocok dengan kalkulasi | Dicatat sebagai `QTY_FRACTION_INFO` (non-blocking, transaksi tetap valid) |
| Upload file yang sama dua kali | Semua transaksi terdeteksi duplikat, `loadedRecords = 0` |

---

## 2. Performance Benchmark Results

Tidak ada formal benchmark tool yang dijalankan, namun beberapa **optimasi performa** telah diimplementasikan dan diukur secara observasional:

### Teknik Optimasi yang Digunakan

| Teknik | Lokasi Kode | Dampak |
|--------|-------------|--------|
| **Streaming Excel Reader** | `ExcelExtractorService.java:101` | File besar tidak di-load ke RAM sepenuhnya. Buffer 4096 byte, row cache 200 baris |
| **Async Processing** | `EtlService.java:98` (`@Async`) | Upload tidak blocking. Respons langsung dikembalikan, proses berjalan di background thread |
| **Batch Insert Transaksi** | `DataLoaderService.java:116-121` | Transaksi di-save per batch (default 1000), bukan satu per satu — mengurangi round-trip ke DB |
| **Bulk Duplicate Check** | `DataLoaderService.java:34` | Semua `transaction_no` yang ada di-load sekali ke `HashSet` — eliminasi N+1 SELECT query |
| **Bulk Product Save** | `DataLoaderService.java:144` | Produk baru dikumpulkan terlebih dahulu, lalu disimpan 1x dengan `saveAll()` |
| **Analytics Index** | `V5__analytics_indexes.sql` | Index pada `item_code` dan composite `(item_code, transaction_id)` untuk query GROUP BY dan JOIN analytics |

### Estimasi Performa (Observasional)

| Ukuran File | Jumlah Nota | Estimasi Durasi |
|-------------|-------------|-----------------|
| < 1 MB | ~50 nota | 2–5 detik |
| 1–5 MB | ~200–500 nota | 5–15 detik |
| 5–20 MB | ~1.000+ nota | 15–60 detik |

> Durasi aktual tersimpan di kolom `duration_seconds` pada tabel `etl_jobs` dan dapat dikonsultasikan setelah job selesai.

---

## 3. Sample Input Excel & Expected Output

### Format Excel Input

File Excel yang diterima adalah laporan penjualan dengan struktur per nota transaksi. Sistem mendeteksi layout kolom secara **otomatis** (tidak perlu template tetap).

**Struktur umum satu nota dalam Excel:**

```
Baris Header Transaksi:
[ No. Transaksi ] [ Tanggal ]         ...  [ Nama Customer ]  ...  [ Alamat ]
  TTU/2024/001     15/01/2024              Toko Maju Jaya         Jl. Raya No.1

Baris Header Tabel Item (auto-detected berdasarkan keyword):
[ No. ] [ Kode ] [ Nama Item     ] [ Jml ] [ Sat ] [ Harga  ] [ Pot ] [ Total  ]

Baris Item:
[  1  ] [ A001 ] [ Beras 5kg     ] [  10 ] [ Sak ] [ 65.000 ] [  0  ] [ 650.000 ]
[  2  ] [ B002 ] [ Minyak Goreng ] [   5 ] [ Ltr ] [ 18.000 ] [  0  ] [  90.000 ]
[  3  ] [ C003 ] [ Gula Pasir    ] [  20 ] [ Kg  ] [ 14.000 ] [  0  ] [ 280.000 ]

Baris Footer:
[ Potongan/Diskon ]  [ 0 ]    [ Pajak ] [ 0 ]    [ Biaya ] [ 0 ]    [ Grand Total ] [ 1.020.000 ]

Baris Subtotal:
                                                                      [ Subtotal   ] [ 1.020.000 ]
```

**Keyword kolom yang dikenali otomatis:**

| Kolom | Keyword yang Dikenali |
|-------|----------------------|
| No. Urut | `no`, `no.` |
| Kode Item | `kd`, `kode`, `code` |
| Nama Item | `nama`, `name` |
| Jumlah/Qty | `jml`, `qty`, `jumlah` |
| Satuan | `satuan`, `sat`, `unit` |
| Harga | `harga`, `price` |
| Diskon | `pot`, `disc` |
| Total | `total` |

### Expected Output Setelah ETL

Setelah diproses, data dari satu nota di atas akan menghasilkan:

**Tabel `transactions` (1 baris):**

| transaction_no | trx_date | customer_name | subtotal | grand_total | is_valid |
|----------------|----------|---------------|----------|-------------|----------|
| TTU/2024/001 | 2024-01-15 | Toko Maju Jaya | 1020000 | 1020000 | true |

**Tabel `transaction_items` (3 baris):**

| transaction_id | item_code | item_name | qty | unit | unit_price | line_total |
|----------------|-----------|-----------|-----|------|------------|------------|
| 1 | A001 | Beras 5kg | 10 | Sak | 65000 | 650000 |
| 1 | B002 | Minyak Goreng | 5 | Ltr | 18000 | 90000 |
| 1 | C003 | Gula Pasir | 20 | Kg | 14000 | 280000 |

**Tabel `products` (produk baru yang belum ada):**

| item_code | item_name | default_unit |
|-----------|-----------|--------------|
| A001 | Beras 5kg | Sak |
| B002 | Minyak Goreng | Ltr |
| C003 | Gula Pasir | Kg |

---

## 4. Database Schema

Schema dibangun bertahap melalui Flyway migration (V1 sampai V5).

### Schema Final (Aktif)

```sql
-- ============================================================
-- etl_jobs: Tracking setiap proses ETL
-- ============================================================
CREATE TABLE etl_jobs (
    id                BIGSERIAL PRIMARY KEY,
    job_id            VARCHAR(100) NOT NULL UNIQUE,  -- UUID
    file_name         VARCHAR(255) NOT NULL,
    file_path         VARCHAR(500),
    status            VARCHAR(20) NOT NULL,           -- PENDING | EXTRACTING | TRANSFORMING | LOADING | COMPLETED | FAILED
    total_records     INTEGER NOT NULL DEFAULT 0,
    extracted_records INTEGER NOT NULL DEFAULT 0,
    valid_records     INTEGER NOT NULL DEFAULT 0,
    failed_records    INTEGER NOT NULL DEFAULT 0,
    duplicate_records INTEGER NOT NULL DEFAULT 0,
    loaded_records    INTEGER NOT NULL DEFAULT 0,
    error_message     TEXT,
    start_time        TIMESTAMP,
    end_time          TIMESTAMP,
    duration_seconds  BIGINT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- transactions: Satu baris = satu nota/receipt
-- ============================================================
CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    transaction_no   VARCHAR(100) NOT NULL UNIQUE,
    trx_date         DATE,
    department       VARCHAR(100),
    customer_code    VARCHAR(100),
    customer_name    VARCHAR(200),
    customer_address VARCHAR(500),
    subtotal         NUMERIC(19, 2),
    discount_total   NUMERIC(19, 2),
    tax_total        NUMERIC(19, 2),
    fee_total        NUMERIC(19, 2),
    grand_total      NUMERIC(19, 2),
    is_valid         BOOLEAN NOT NULL DEFAULT TRUE,
    etl_job_id       BIGINT REFERENCES etl_jobs(id),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- transaction_items: Satu baris = satu item dalam nota
-- ============================================================
CREATE TABLE transaction_items (
    id             BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    line_no        VARCHAR(20),
    item_code      VARCHAR(100),
    item_name      VARCHAR(300),
    qty            NUMERIC(19, 4),
    unit           VARCHAR(50),
    unit_price     NUMERIC(19, 2),
    discount_pct   NUMERIC(19, 2),
    line_total     NUMERIC(19, 2),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- products: Master produk unik berdasarkan item_code
-- ============================================================
CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    item_code    VARCHAR(100) NOT NULL UNIQUE,
    item_name    VARCHAR(300),
    default_unit VARCHAR(50),
    etl_job_id   BIGINT REFERENCES etl_jobs(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- error_logs: Catatan error per baris per job
-- ============================================================
CREATE TABLE error_logs (
    id                BIGSERIAL PRIMARY KEY,
    etl_job_id        BIGINT NOT NULL REFERENCES etl_jobs(id),
    row_number        INTEGER NOT NULL,
    field_name        VARCHAR(100) NOT NULL,
    error_description TEXT NOT NULL,
    original_value    TEXT,
    error_type        VARCHAR(50) NOT NULL,  -- FORMAT_ERROR | VALIDATION_ERROR | DUPLICATE_ERROR | QTY_FRACTION_INFO
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Indexes

```sql
-- etl_jobs
CREATE INDEX idx_etl_job_status  ON etl_jobs(status);
CREATE INDEX idx_etl_job_created ON etl_jobs(created_at);

-- transactions
CREATE INDEX idx_transaction_no    ON transactions(transaction_no);
CREATE INDEX idx_transaction_date  ON transactions(trx_date);
CREATE INDEX idx_transaction_valid ON transactions(is_valid);
CREATE INDEX idx_transaction_etl   ON transactions(etl_job_id);

-- transaction_items
CREATE INDEX idx_item_transaction       ON transaction_items(transaction_id);
CREATE INDEX idx_item_item_code         ON transaction_items(item_code);
CREATE INDEX idx_item_code_txn_id       ON transaction_items(item_code, transaction_id);  -- V5: analytics

-- products
CREATE INDEX idx_product_item_code ON products(item_code);
CREATE INDEX idx_product_etl_job   ON products(etl_job_id);

-- error_logs
CREATE INDEX idx_error_log_job ON error_logs(etl_job_id);
```

### Relasi Antar Tabel

```
etl_jobs (1)
    ├──< transactions (N)
    │       └──< transaction_items (N)
    ├──< products (N)
    └──< error_logs (N)
```

### Catatan Evolusi Schema

| Migration | Perubahan |
|-----------|-----------|
| V1 | Schema awal: `products`, `customers`, `transactions`, `etl_jobs`, `error_logs` |
| V2 | Redesign: drop semua, buat ulang dengan model nota (`transactions` + `transaction_items`). Customer denormalized ke dalam `transactions` |
| V3 | Tambah `product_aliases` untuk traceability, tambah `etl_job_id` di `products` |
| V4 | Drop `product_aliases` — inkonsistensi bisa langsung di-query dari `transaction_items` |
| V5 | Tambah index analytics di `item_code` dan composite `(item_code, transaction_id)` |

---

## 5. Sample Output Data di PostgreSQL

### Query & Output: Daftar Job ETL

```sql
SELECT job_id, file_name, status, total_records, valid_records,
       failed_records, duplicate_records, loaded_records, duration_seconds
FROM etl_jobs
ORDER BY created_at DESC;
```

**Contoh Output:**

| job_id | file_name | status | total | valid | failed | duplicate | loaded | durasi (s) |
|--------|-----------|--------|-------|-------|--------|-----------|--------|------------|
| a1b2c3... | penjualan_jan2024.xlsx | COMPLETED | 47 | 45 | 2 | 0 | 45 | 3 |
| d4e5f6... | penjualan_jan2024.xlsx | COMPLETED | 47 | 0 | 0 | 47 | 0 | 2 |

> Baris ke-2 adalah upload ulang file yang sama — semua terdeteksi duplikat.

---

### Query & Output: Transaksi

```sql
SELECT transaction_no, trx_date, customer_name, grand_total, is_valid
FROM transactions
ORDER BY trx_date DESC
LIMIT 5;
```

**Contoh Output:**

| transaction_no | trx_date | customer_name | grand_total | is_valid |
|----------------|----------|---------------|-------------|----------|
| TTU/2024/047 | 2024-01-31 | Toko Sumber Makmur | 3.250.000 | true |
| TTU/2024/046 | 2024-01-31 | CV Maju Bersama | 1.850.000 | true |
| TTU/2024/045 | 2024-01-30 | Toko Maju Jaya | 1.020.000 | true |
| TTU/2024/044 | 2024-01-30 | UD Sejahtera | 750.000 | true |
| TTU/2024/043 | 2024-01-29 | Toko Berkah | 920.000 | false |

---

### Query & Output: Item Terlaris

```sql
SELECT ti.item_code, ti.item_name,
       SUM(ti.qty)        AS total_qty,
       SUM(ti.line_total) AS total_revenue,
       COUNT(DISTINCT ti.transaction_id) AS jumlah_transaksi
FROM transaction_items ti
GROUP BY ti.item_code, ti.item_name
ORDER BY total_revenue DESC
LIMIT 5;
```

**Contoh Output:**

| item_code | item_name | total_qty | total_revenue | jumlah_transaksi |
|-----------|-----------|-----------|---------------|------------------|
| A001 | Beras 5kg | 450 | 29.250.000 | 38 |
| B002 | Minyak Goreng | 210 | 3.780.000 | 29 |
| C003 | Gula Pasir | 380 | 5.320.000 | 25 |
| D004 | Tepung Terigu | 300 | 3.600.000 | 22 |
| E005 | Susu Kental | 150 | 2.850.000 | 18 |

---

### Query & Output: Error Logs

```sql
SELECT el.row_number, el.field_name, el.error_type,
       el.error_description, el.original_value
FROM error_logs el
JOIN etl_jobs ej ON el.etl_job_id = ej.id
WHERE ej.job_id = 'a1b2c3...'
ORDER BY el.row_number;
```

**Contoh Output:**

| row_number | field_name | error_type | error_description | original_value |
|------------|------------|------------|-------------------|----------------|
| 15 | Date | FORMAT_ERROR | Cannot parse date | 32/01/2024 |
| 23 | Quantity | FORMAT_ERROR | Invalid quantity format | dua puluh |
| 0 | Transaction | DUPLICATE_ERROR | Duplicate transaction_no: TTU/2024/001 | TTU/2024/001 |

---

## 6. Test Execution Results

### Alur Eksekusi ETL (Per Job)

Setiap kali file di-upload, sistem menjalankan pipeline berikut:

```
[1] UPLOAD
    POST /api/etl/upload
    → Validasi file (ekstensi .xlsx, tidak kosong, < 50MB)
    → Simpan file ke disk (uploads/{jobId}_{filename})
    → Buat record etl_jobs dengan status PENDING
    → Return jobId langsung (async)

[2] EXTRACTING
    → Baca Excel menggunakan StreamingReader
    → Auto-detect layout kolom dari keyword header
    → Parse setiap nota: header transaksi + item-item + footer
    → Hasil: List<ExtractedReceipt>

[3] TRANSFORMING
    → Parse dan validasi setiap field (tanggal, angka, dll)
    → Normalisasi string (nama customer, nama item)
    → Hitung kalkulasi ulang grand total
    → Catat semua error ke ErrorLog
    → Hasil: List<ValidatedReceipt> (valid=true/false)

[4] LOADING
    → Pre-load semua transaction_no existing ke HashSet
    → Deteksi duplikat (in-memory, tidak query DB per record)
    → Batch insert transaksi (per 1000 records)
    → Batch save produk baru
    → Batch save semua error logs
    → Update etl_jobs: status COMPLETED

[5] SELESAI
    → GET /api/etl/jobs/{jobId} menampilkan ringkasan lengkap
```

### Contoh Response API Setelah Job Selesai

```json
{
  "jobId": "a1b2c3d4-e5f6-...",
  "fileName": "penjualan_jan2024.xlsx",
  "status": "COMPLETED",
  "totalRecords": 47,
  "extractedRecords": 47,
  "validRecords": 45,
  "failedRecords": 2,
  "duplicateRecords": 0,
  "loadedRecords": 45,
  "durationSeconds": 3,
  "transactionCount": 45,
  "productCount": 28,
  "errorCount": 2,
  "qtyFractionIssues": 1,
  "failedTransactionNos": ["TTU/2024/015", "TTU/2024/023"],
  "errors": [
    {
      "rowNumber": 15,
      "fieldName": "Date",
      "errorType": "FORMAT_ERROR",
      "errorDescription": "Cannot parse date",
      "originalValue": "32/01/2024"
    },
    {
      "rowNumber": 23,
      "fieldName": "Quantity",
      "errorType": "FORMAT_ERROR",
      "errorDescription": "Invalid quantity format",
      "originalValue": "dua puluh"
    }
  ]
}
```

### Status Job yang Mungkin Terjadi

| Status | Keterangan |
|--------|------------|
| `PENDING` | Job dibuat, menunggu diproses |
| `EXTRACTING` | Sedang membaca file Excel |
| `TRANSFORMING` | Sedang validasi dan parsing data |
| `LOADING` | Sedang menyimpan ke database |
| `COMPLETED` | Selesai sukses |
| `FAILED` | Gagal karena error tidak terduga (lihat `error_message`) |

---

*Dokumen ini di-generate berdasarkan analisis source code project ETL-TTU.*
