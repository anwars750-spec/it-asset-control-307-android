# IT Asset Control 307 — Android

Aplikasi Android untuk stock opname IT MDS Sukabumi 307. Ini adalah pembungkus Android WebView untuk aplikasi Apps Script yang sudah digunakan, **bukan** backend baru. Google Sheets dan Drive tetap menjadi penyimpanan data.

## Cara mendapatkan APK
1. Buka tab **Actions** pada repository ini.
2. Pilih workflow **Build Android APK** dan lihat run dari push terbaru.
3. Setelah status hijau, klik artifact **IT-Asset-Control-307-APK** lalu unduh ZIP.
4. Ekstrak dan install `IT-Asset-Control-307-debug.apk` di HP Android. Saat Android meminta izin pemasangan dari sumber tersebut, izinkan hanya jika file berasal dari repository ini.

## Perhatian
- APK memuat URL Apps Script online, sehingga membutuhkan internet. Tampilan halaman tetap mengikuti kode Apps Script yang ter-deploy.
- Versi awal ini **debug/test APK**. Pengujian Android wajib dilakukan untuk input SN, scan foto barcode, upload foto, dan penyimpanan ke Google Sheets.
- Login Google dapat ditolak dalam WebView. Jika terjadi, buka halaman melalui Chrome; jangan menonaktifkan keamanan akun hanya untuk menyiasatinya.
- Jangan menyebarkan APK maupun link pengeditan inventaris ke publik. Deployment Apps Script yang memakai “Execute as me” + “Anyone” berisiko memberi pihak lain akses mengubah data dengan hak akun pemilik.
- Aplikasi ini independen dan tidak berafiliasi resmi dengan PT Matahari.

## Teknologi
Android Java / WebView, kamera + pilihan file melalui Android chooser, fitur unduh via DownloadManager untuk HTTPS, build gratis menggunakan GitHub Actions / Android Gradle Plugin. Pengembangan dilakukan tanpa Lovable dan tanpa migrasi database.

## Alamat web
https://script.google.com/macros/s/AKfycbyGGoMSfhSTVIfUQJHgeUm4h7WIt8hvhXmHYSW6VbBwTWIZKey4p8KUImCOVTKCBtkU8w/exec
