# WiFi Share

Aplikasi Android untuk mengelola server HTTP lokal langsung dari perangkat. Komputer atau ponsel lain yang berada di satu jaringan Wi-Fi dapat mengakses berkas penyimpanan lewat browser tanpa perlu kabel data atau koneksi internet luar.

## Unduh Aplikasi

File APK siap pakai tersedia pada folder `release/`:
- [Unduh WiFiShare.apk](release/WiFiShare.apk) (Versi Debug, 5.7 MB)

Pemasangan manual:
1. Salin berkas `WiFiShare.apk` ke penyimpanan ponsel.
2. Buka berkas APK melalui pengelola file di Android dan pilih Pasang.
3. Berikan izin instalasi dari sumber tidak dikenal jika diminta oleh sistem.

## Fitur

- Navigasi direktori penyimpanan internal dan subfolder secara bertingkat.
- Pencegahan path traversal agar akses direktori tetap berada pada folder yang ditentukan.
- Filter pencarian instan pada browser untuk memfilter nama berkas tanpa memuat ulang halaman.
- Pemutaran langsung untuk format media gambar, audio, dan video.
- Pengunduhan berkas dokumen, arsip, dan format lainnya.
- Antarmuka hitam putih tanpa gradasi dengan tombol rounded pill.

## Kebutuhan Sistem

- Perangkat Android dengan versi minimal Android 7.0 (API tingkat 24).
- Izin akses jaringan lokal (`INTERNET`, `ACCESS_WIFI_STATE`).
- Izin pengelolaan file (`MANAGE_EXTERNAL_STORAGE`) untuk Android 11 ke atas agar dapat membaca direktori penyimpanan utama.

## Cara Penggunaan

1. Pastikan ponsel dan komputer terhubung ke titik akses Wi-Fi yang sama.
2. Buka aplikasi WiFi Share di ponsel.
3. Tekan tombol Mulai Server.
4. Periksa alamat IP yang muncul di layar, misalnya `http://192.168.1.15:8080`.
5. Buka peramban di komputer, lalu ketik alamat tersebut pada bilah alamat.
6. Daftar berkas ponsel akan langsung terbuka di halaman browser.

## Kompilasi Mandiri

Jika ingin melakukan build dari kode sumber:
1. Buka direktori proyek menggunakan Android Studio.
2. Sinkronkan proyek dengan Gradle.
3. Pilih menu Build, lalu pilih Build APKs.
