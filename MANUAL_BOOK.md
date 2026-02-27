# Manual Book - ETL TTU Application
## Sistem Extract, Transform, Load untuk Data Nota Penjualan

**Versi:** 1.0.0
**Teknologi:** Spring Boot 3.2.2, Java 17, PostgreSQL, Apache POI

---

## 1. Gambaran Umum

ETL TTU adalah aplikasi backend yang memproses file Excel (.xlsx) berisi data nota/receipt penjualan, melakukan validasi, dan menyimpan hasilnya ke database PostgreSQL dalam format yang ternormalisasi.

### Alur Proses (Pipeline)

```
Upload .xlsx  →  EXTRACT  →  TRANSFORM  →  LOAD  →  Database
                    ↓            ↓           ↓
              Parsing nota   Validasi    3 tabel:
              dari Excel     & parsing   - transactions
                             angka       - transaction_items
                                         - products
```

1. **EXTRACT** — Membaca file Excel, mengenali struktur nota (header, items, footer), menghasilkan daftar receipt mentah
2. **TRANSFORM** — Validasi data, parsing angka (termasuk format Indonesia dan pecahan), validasi grand total
3. **LOAD** — Simpan ke 3 tabel database yang ternormalisasi

---

## 2. Persyaratan Sistem

| Komponen       | Versi Minimum     |
|----------------|-------------------|
| Java           | 17                |
| PostgreSQL     | 12+               |
| Maven          | 3.8+              |
| RAM            | 2 GB (minimum)    |
| Disk           | Sesuai ukuran file upload |

### Setup Database

```sql
CREATE DATABASE etl_ttu_db;
```

Konfigurasi koneksi ada di `application.yml`:
- **URL:** `jdbc:postgresql://localhost:5432/etl_ttu_db`
- **User/Password:** `postgres/postgres` (ubah sesuai kebutuhan)

### Menjalankan Aplikasi

```bash
mvn spring-boot:run
```

Aplikasi berjalan di: `http://localhost:8080/api`

---

## 3. Struktur Format Excel yang Didukung

Aplikasi ini dirancang untuk membaca file Excel dengan struktur nota berikut:

### 3.1 Baris Header Nota (Transaction Header)

| Kolom (index) | Isi                | Contoh                        |
|---------------|--------------------|-------------------------------|
| 1             | Nomor Transaksi    | `000001/KSR/UTM/1025`        |
| 7             | Tanggal            | `2025-10-15` atau format date |
| 10            | Department         | `SALES`                       |
| 12            | Kode Customer      | `C001`                        |
| 16            | Nama Customer      | `PT Maju Jaya`                |
| 24            | Alamat             | `Jl. Sudirman No. 1`         |

**Deteksi:** Kolom 1 harus cocok pola `\d{6}/.*` (6 digit + slash)

### 3.2 Baris Header Tabel Item

Dikenali dari: Kolom 1 = `"No."` dan Kolom 7 = `"Nama Item"`

### 3.3 Baris Data Item

| Kolom (index) | Isi           | Contoh         |
|---------------|---------------|----------------|
| 1             | No. Urut      | `1`            |
| 2             | Kode Item     | `ITM001`       |
| 7             | Nama Item     | `Semen Portland` |
| 17            | Quantity      | `1,00 1/4 KG`  |
| 19            | Satuan        | `KG`           |
| 24            | Harga Satuan  | `85.000`       |
| 25            | Diskon (%)    | `5`            |
| 29            | Total Baris   | `80.750`       |

**Deteksi:** Kolom 1 = angka (numeric cell) dan Kolom 2 = tidak kosong

### 3.4 Baris Summary

| Kolom (index) | Isi       |
|---------------|-----------|
| 29            | Subtotal  |

### 3.5 Baris Footer

Dikenali dari: Kolom 1 dimulai dengan `"Pot."`

| Kolom (index) | Isi          |
|---------------|--------------|
| 3             | Diskon Total |
| 11            | Pajak        |
| 18            | Biaya        |
| 28            | Grand Total  |

---

## 4. API Endpoints

Base URL: `http://localhost:8080/api`

### 4.1 Upload File

```
POST /etl/jobs
Content-Type: multipart/form-data
Parameter: file (file .xlsx)
```

**Response:**
```json
{
  "jobId": "uuid-string",
  "message": "File uploaded successfully. Processing started.",
  "status": "PENDING"
}
```

Proses berjalan **asynchronous** — response langsung dikembalikan, proses ETL jalan di background.

### 4.2 List Semua Job (Paginated)

```
GET /etl/jobs?page=0&size=10
```

Menampilkan semua job yang pernah diproses beserta statistiknya.

### 4.3 Detail Satu Job

```
GET /etl/jobs/{jobId}
```

**Response:**
```json
{
  "jobId": "...",
  "fileName": "ADHI.xlsx",
  "status": "COMPLETED",
  "totalRecords": 3300,
  "extractedRecords": 3300,
  "validRecords": 3217,
  "failedRecords": 83,
  "duplicateRecords": 0,
  "loadedRecords": 3300,
  "durationSeconds": 45
}
```

**Status yang mungkin:** `PENDING` → `EXTRACTING` → `TRANSFORMING` → `LOADING` → `COMPLETED` atau `FAILED`

### 4.4 List Produk

```
GET /etl/products
GET /etl/products?inconsistencies=true
```

Menampilkan semua produk yang ter-ekstrak. Parameter `inconsistencies=true` memfilter produk dengan nama atau satuan tidak konsisten antar file.

### 4.5 Detail Produk

```
GET /etl/products/{itemCode}
```

Menampilkan detail satu produk berdasarkan kode item.

### 4.6 Daftar Tanggal Transaksi

```
GET /etl/transactions/dates
```

Menampilkan semua tanggal yang memiliki data transaksi beserta jumlahnya. Berguna untuk kalender atau filter tanggal di frontend.

**Response:**
```json
[
  { "date": "2025-10-15", "count": 120 },
  { "date": "2025-10-16", "count": 85 }
]
```

### 4.7 List Transaksi per Tanggal

```
GET /etl/transactions?date=YYYY-MM-DD
```

Menampilkan semua transaksi pada tanggal tertentu.

### 4.8 Detail Transaksi

```
GET /etl/transactions/detail?no=000001/KSR/UTM/1025
```

Menampilkan detail lengkap satu transaksi beserta semua item di dalamnya.

---

## 5. Struktur Database

### 5.1 Tabel `transactions` (1 baris per nota)

| Kolom              | Tipe            | Keterangan                    |
|--------------------|-----------------|-------------------------------|
| id                 | BIGSERIAL (PK)  | Auto-increment                |
| transaction_no     | VARCHAR(100)    | **UNIQUE** — nomor nota       |
| trx_date           | DATE            | Tanggal transaksi             |
| department         | VARCHAR(100)    | Department                    |
| customer_code      | VARCHAR(100)    | Kode customer                 |
| customer_name      | VARCHAR(200)    | Nama customer                 |
| customer_address   | VARCHAR(500)    | Alamat                        |
| subtotal           | NUMERIC(19,2)   | Subtotal dari summary row     |
| discount_total     | NUMERIC(19,2)   | Diskon total dari footer      |
| tax_total          | NUMERIC(19,2)   | Pajak dari footer             |
| fee_total          | NUMERIC(19,2)   | Biaya dari footer             |
| grand_total        | NUMERIC(19,2)   | Total akhir dari footer       |
| is_valid           | BOOLEAN         | Lolos validasi atau tidak     |
| etl_job_id         | BIGINT (FK)     | Referensi ke etl_jobs         |

### 5.2 Tabel `transaction_items` (1 baris per item dalam nota)

| Kolom              | Tipe            | Keterangan                    |
|--------------------|-----------------|-------------------------------|
| id                 | BIGSERIAL (PK)  | Auto-increment                |
| transaction_id     | BIGINT (FK)     | Referensi ke transactions     |
| line_no            | VARCHAR(20)     | Nomor urut dalam nota         |
| item_code          | VARCHAR(100)    | Kode barang                   |
| item_name          | VARCHAR(300)    | Nama barang                   |
| qty                | NUMERIC(19,4)   | Jumlah (4 desimal untuk pecahan) |
| unit               | VARCHAR(50)     | Satuan (KG, PCS, dll)         |
| unit_price         | NUMERIC(19,2)   | Harga satuan                  |
| discount_pct       | NUMERIC(19,2)   | Persentase diskon             |
| line_total         | NUMERIC(19,2)   | Total baris                   |

### 5.3 Tabel `products` (1 baris per kode barang unik)

| Kolom              | Tipe            | Keterangan                    |
|--------------------|-----------------|-------------------------------|
| id                 | BIGSERIAL (PK)  | Auto-increment                |
| item_code          | VARCHAR(100)    | **UNIQUE** — kode barang      |
| item_name          | VARCHAR(300)    | Nama barang                   |
| default_unit       | VARCHAR(50)     | Satuan default                |

### 5.4 Tabel Pendukung

- **`etl_jobs`** — Tracking status dan statistik setiap proses upload
- **`error_logs`** — Log error per baris, termasuk tipe error dan nilai asli

---

## 6. Validasi yang Dilakukan

### 6.1 Validasi Tanggal
- Tanggal harus bisa di-parse (format ISO, dd/MM/yyyy, dd-MM-yyyy, dll)
- Nota dengan tanggal yang gagal di-parse ditandai **invalid**

### 6.2 Validasi Angka
- Mendukung format Indonesia: `1.234.567,89` (titik = ribuan, koma = desimal)
- Mendukung format standar: `1,234,567.89`
- Mendukung simbol mata uang: `Rp`, `$`, `€`
- Mendukung pecahan quantity: `1,00 1/4 KG` → 1.25 KG

### 6.3 Validasi Grand Total
```
Formula: grand_total ≈ subtotal - discount_total + tax_total + fee_total
Toleransi: ±1.00 (untuk pembulatan)
```
Nota yang tidak lolos validasi grand total ditandai `is_valid = false` tapi **tetap disimpan** ke database.

### 6.4 Deteksi Duplikat
- Berdasarkan `transaction_no` (UNIQUE constraint)
- Nota duplikat **tidak disimpan ulang**, dicatat sebagai error

---

## 7. Batasan (Limitations)

### 7.1 Format File
- **Hanya mendukung `.xlsx`** (Excel 2007+), tidak mendukung `.xls` (Excel 97-2003)
- **Maksimal ukuran file: 50 MB**
- Hanya membaca **sheet pertama** (index 0)

### 7.2 Struktur Excel
- **Harus mengikuti format kolom yang sudah ditentukan** (lihat Bagian 3). Jika kolom bergeser, data tidak akan terbaca dengan benar
- **Nomor transaksi harus berpola `\d{6}/...`** (6 digit diikuti slash). Pola lain tidak dikenali sebagai header nota
- **Header tabel item harus persis** `"No."` di kolom 1 dan `"Nama Item"` di kolom 7
- **Footer harus dimulai dengan `"Pot."`** di kolom 1
- Jika nota tidak memiliki footer, nilai subtotal/diskon/pajak/biaya/grand_total akan bernilai `NULL`

### 7.3 Parsing Quantity
- Format pecahan yang didukung: `1,00 1/4 KG`, `1/2 KG`, `2,50 KG`, `3`
- **Tidak mendukung** pecahan kompleks seperti `1 3/16` atau notasi desimal campuran dengan pecahan ganda
- Jika quantity tidak bisa di-parse, nota ditandai **invalid**

### 7.4 Pemrosesan
- Proses berjalan **asynchronous** — tidak ada progress bar real-time
- **Satu file diproses dalam satu transaksi database** — jika terjadi error fatal di tengah proses, seluruh load di-rollback
- **Batch size: 1000 nota per batch** — file besar membutuhkan waktu lebih lama
- **Timeout transaksi: 30 menit (1800 detik)** — file sangat besar mungkin timeout

### 7.5 Duplikasi
- Pengecekan duplikat berdasarkan `transaction_no` saja
- Jika file yang **sama** di-upload ulang, semua nota akan terdeteksi sebagai duplikat dan **tidak disimpan ulang**
- Tidak ada fitur **update/overwrite** data yang sudah ada

### 7.6 Produk
- Produk di-deduplikasi berdasarkan `item_code`
- Jika produk yang sama muncul dengan nama berbeda di nota berbeda, **nama dari kemunculan pertama yang disimpan** — tidak di-update

### 7.7 Validasi
- Nota yang gagal validasi **tetap disimpan** ke database dengan `is_valid = false`
- Validasi grand total menggunakan toleransi ±1.00 — selisih kecil akibat pembulatan dianggap valid
- **Tidak ada validasi bisnis lanjutan** seperti: cek stok, cek limit kredit customer, dll

### 7.8 Concurrency
- **Tidak ada pembatasan upload paralel** — jika 2 file di-upload bersamaan, keduanya diproses secara paralel
- Bisa menyebabkan masalah performa pada database jika file sangat besar

### 7.9 Keamanan
- **Tidak ada autentikasi/otorisasi** — semua endpoint terbuka
- **Tidak ada rate limiting** pada upload
- File yang di-upload disimpan di folder `uploads/` tanpa enkripsi

---

## 8. Tipe Error yang Dicatat

| Error Type            | Keterangan                                      |
|-----------------------|-------------------------------------------------|
| `VALIDATION_ERROR`    | Error validasi umum                             |
| `FORMAT_ERROR`        | Format angka atau tanggal tidak valid           |
| `DUPLICATE_ERROR`     | Nota duplikat (transaction_no sudah ada)        |
| `NULL_VALUE_ERROR`    | Field wajib kosong/null                         |
| `BUSINESS_RULE_ERROR` | Gagal validasi grand total                      |
| `QTY_FRACTION_INFO`   | Info: quantity mengandung pecahan (bukan error)  |

---

## 9. Troubleshooting

| Masalah | Penyebab | Solusi |
|---------|----------|--------|
| Status tetap `EXTRACTING` | File sangat besar / format tidak sesuai | Cek log di `logs/etl-ttu.log` |
| `extractedRecords = 0` | Format Excel tidak sesuai pola yang diharapkan | Pastikan nomor transaksi berpola 6 digit + slash |
| `failedRecords` tinggi | Banyak nota gagal validasi grand total | Cek detail job di `GET /etl/jobs/{jobId}`, periksa apakah footer terbaca |
| `FAILED` status | Error fatal saat proses | Cek `errorMessage` di `GET /etl/jobs/{jobId}` dan log file |
| Duplicate key error | Upload file yang sama 2x | Data sudah ada, tidak perlu upload ulang |
| Timeout | File terlalu besar (>3000+ nota) | Tingkatkan `etl.transaction-timeout` di config |
| Produk inkonsisten | Nama/satuan produk berbeda antar file | Gunakan `GET /etl/products?inconsistencies=true` untuk mengidentifikasi |

---

## 10. Contoh Penggunaan (Postman/cURL)

### Upload File
```bash
curl -X POST http://localhost:8080/api/etl/jobs \
  -F "file=@ADHI.xlsx"
```

### List Semua Job
```bash
curl "http://localhost:8080/api/etl/jobs?page=0&size=10"
```

### Cek Detail Job
```bash
curl http://localhost:8080/api/etl/jobs/{jobId}
```

### List Produk
```bash
curl http://localhost:8080/api/etl/products
```

### List Produk Inkonsisten
```bash
curl "http://localhost:8080/api/etl/products?inconsistencies=true"
```

### Detail Produk
```bash
curl http://localhost:8080/api/etl/products/ITM001
```

### Daftar Tanggal Transaksi
```bash
curl http://localhost:8080/api/etl/transactions/dates
```

### Transaksi per Tanggal
```bash
curl "http://localhost:8080/api/etl/transactions?date=2025-10-15"
```

### Detail Transaksi
```bash
curl "http://localhost:8080/api/etl/transactions/detail?no=000001/KSR/UTM/1025"
```
