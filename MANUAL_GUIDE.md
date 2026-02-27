# Manual Guide ETL TTU

Dokumen ini adalah panduan penggunaan aplikasi ETL TTU berdasarkan implementasi source code saat ini.

## 1. Tujuan Aplikasi

ETL TTU memproses file Excel nota penjualan (`.xlsx`) menjadi data terstruktur ke PostgreSQL.
Alur utama:

1. Upload file.
2. Extract data nota dan item dari sheet Excel.
3. Transform dan validasi data.
4. Load ke tabel transaksi, item, dan produk.
5. Sediakan report, status job, dan export CSV.

## 2. Stack dan Prasyarat

- Java 17
- Maven 3.8+
- PostgreSQL 12+
- Spring Boot 3.2.2

## 3. Konfigurasi Penting

File konfigurasi: `src/main/resources/application.yml`

- Context path API: `/api`
- Port default: `8080`
- Upload max file: `50MB`
- Database default: `jdbc:postgresql://localhost:5432/etl_ttu_db`
- Folder upload default: `uploads`

Catatan:
- Flyway aktif dan menjalankan migrasi di `classpath:db/migration`.
- Nilai `etl.transaction-timeout`, `etl.max-years-back`, dan `etl.temp-dir` ada di config, tetapi belum dipakai langsung di alur ETL saat ini.

## 4. Menjalankan Aplikasi

1. Buat database:

```sql
CREATE DATABASE etl_ttu_db;
```

2. Sesuaikan koneksi database di `application.yml`.
3. Jalankan aplikasi:

```bash
mvn spring-boot:run
```

Base URL API:

```text
http://localhost:8080/api
```

## 5. Format Excel yang Didukung

Aplikasi membaca **sheet pertama saja** (`sheet index 0`) dan mendeteksi struktur dengan posisi kolom tetap.

### 5.1 Header Nota

Deteksi header nota terjadi jika:
- kolom index `1` (kolom B) cocok regex `\d{6}/.*`
- kolom index `7` (kolom H) berisi tanggal

Field yang diambil:
- `transactionNo` dari kolom 1
- `date` dari kolom 7
- `department` dari kolom 10
- `customerCode` dari kolom 12
- `customerName` dari kolom 16
- `address` dari kolom 24

### 5.2 Header Tabel Item

Dikenali jika:
- kolom 1 bernilai persis `No.`
- kolom 7 bernilai persis `Nama Item`

### 5.3 Baris Item

Dianggap item jika:
- kolom 1 bertipe numeric
- kolom 2 (item code) tidak kosong

Field item:
- line no: kolom 1
- item code: kolom 2
- item name: kolom 7
- qty: kolom 17
- unit: kolom 19
- unit price: kolom 24
- discount: kolom 25
- line total: kolom 29

### 5.4 Summary dan Footer

- Subtotal diambil dari baris non-item jika kolom 29 berisi angka-like.
- Footer dikenali jika kolom 1 diawali `Pot.`
- Pada footer:
  - discount total: kolom 3
  - tax total: kolom 11
  - fee total: kolom 18
  - grand total: kolom 28

## 6. Aturan Transformasi dan Validasi

### 6.1 Validasi Tanggal

Tanggal diparse dengan format berikut:
- `dd/MM/yyyy`
- `dd-MM-yyyy`
- `yyyy-MM-dd`
- `dd/MM/yy`
- `dd-MM-yy`
- `d/M/yyyy`
- `d-M-yyyy`

Jika gagal parse, receipt ditandai invalid.

### 6.2 Validasi Angka

Parser angka mendukung format umum berikut:
- Indonesia: `1.234.567,89`
- Internasional: `1,234,567.89`
- Decimal comma: `1234,89`
- Simbol mata uang tertentu akan dibersihkan (`Rp`, `$`, dll)

### 6.3 Parsing Quantity

Contoh yang didukung:
- `1,00 1/4 KG`
- `1/2 KG`
- `2,50 KG`
- `3`

Jika quantity pecahan terdeteksi, sistem menambahkan log bertipe `QTY_FRACTION_INFO`.

### 6.4 Validasi Grand Total

Formula validasi:

```text
grand_total ~ subtotal - discount_total + tax_total + fee_total
```

Toleransi selisih: `1.00`.

Jika selisih melebihi toleransi, receipt ditandai invalid (`is_valid = false`).

## 7. API Endpoint

Base path: `/api/etl`

### ETL Jobs

1. Upload file

```http
POST /jobs
Content-Type: multipart/form-data
form-data: file=<file.xlsx>
```

2. List semua job (pagination)

```http
GET /jobs?page=0&size=10
```

3. Detail satu job

```http
GET /jobs/{jobId}
```

### Products

4. List semua produk

```http
GET /products
GET /products?inconsistencies=true
```

5. Detail satu produk

```http
GET /products/{itemCode}
```

### Transactions

6. Daftar tanggal yang ada transaksinya

```http
GET /transactions/dates
```

7. List transaksi per tanggal

```http
GET /transactions?date=YYYY-MM-DD
```

8. Detail satu transaksi beserta item-nya

```http
GET /transactions/detail?no=000001/KSR/UTM/1025
```

## 8. Status Job

Status yang digunakan:
- `PENDING`
- `EXTRACTING`
- `TRANSFORMING`
- `LOADING`
- `COMPLETED`
- `FAILED`

## 9. Data yang Disimpan

Tabel utama:
- `etl_jobs`
- `error_logs`
- `transactions`
- `transaction_items`
- `products`

Kunci penting:
- `transactions.transaction_no` unik
- `products.item_code` unik

## 10. Format Error Response API

Error response mengikuti struktur:

```json
{
  "timestamp": "2026-02-13T16:00:00",
  "status": 400,
  "error": "ETL Error",
  "message": "Only .xlsx files are supported",
  "path": "/api/etl/upload"
}
```

Status umum:
- `400` untuk ETL error
- `404` untuk job tidak ditemukan
- `413` untuk file melebihi ukuran maksimal
- `500` untuk error tak terduga

## 11. Batasan Aplikasi

1. Hanya menerima file dengan nama berakhiran `.xlsx`.
2. Validasi tipe file berbasis nama file, bukan MIME sniffing.
3. Maksimum ukuran upload adalah 50MB.
4. Hanya membaca sheet pertama Excel.
5. Posisi kolom wajib tetap; pergeseran layout akan menyebabkan salah baca.
6. Header item harus persis `No.` dan `Nama Item` (case-sensitive).
7. Nomor transaksi harus cocok regex `\d{6}/.*` agar dikenali sebagai header nota.
8. Rentang tanggal (misal maksimal mundur N tahun) belum dipaksa meski ada properti `etl.max-years-back`.
9. Properti `etl.transaction-timeout` belum dipakai langsung sebagai timeout transaksi Spring.
10. Properti `etl.temp-dir` belum dipakai dalam alur proses.
11. Duplicate transaksi dicek terhadap data yang sudah ada di database berdasarkan `transaction_no`.
12. Tidak ada endpoint untuk update atau overwrite transaksi yang sudah masuk.
13. Produk deduplicate berdasarkan `item_code`; data produk yang sudah ada tidak diupdate otomatis.
14. Tidak ada autentikasi, otorisasi, dan rate limiting pada endpoint.
15. File upload disimpan ke disk lokal folder `uploads` tanpa enkripsi bawaan.
16. Export CSV tidak menyediakan filter kolom atau kriteria tambahan.
17. Error report (`/report/{jobId}`) akan gagal jika job tidak punya error log.
18. Proses ETL berjalan async; kontrol kapasitas worker belum dikustomisasi di project ini.

## 12. Troubleshooting Singkat

1. Upload ditolak
- Periksa ekstensi file (`.xlsx`) dan ukuran file (< 50MB).

2. `extractedRecords` kecil atau 0
- Cek format template Excel, terutama posisi kolom dan marker header item.

3. Banyak `failedRecords`
- Cek endpoint `GET /jobs/{jobId}` untuk melihat statistik dan error dari job tersebut.

4. Job `FAILED`
- Cek field `errorMessage` pada endpoint `GET /jobs/{jobId}` dan log file `logs/etl-ttu.log`.

5. Produk dengan data tidak konsisten
- Gunakan `GET /products?inconsistencies=true` untuk melihat produk yang memiliki nama atau satuan tidak konsisten antar file.
