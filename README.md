# telegram-web

Klien Telegram Web Android berbasis Kotlin dan WebView. Pada versi ini sudah tersedia kunci aplikasi PIN/biometrik, penyimpanan metadata terenkripsi, dan pengelolaan beberapa akun melalui profil WebView yang terisolasi.

## Struktur repo

```text
.
├── .github/workflows/build-apk.yml
├── prd/                                  # PRD dan target produk
├── telegram-web-client/                  # Proyek Android
│   ├── app/src/main/java/.../accounts/   # penyimpanan akun dan profil WebView
│   ├── app/src/main/java/.../security/   # derivasi PIN dan security store
│   ├── app/src/main/res/                 # layout dan resource
│   └── tools/badge_hook.test.js           # tes hook badge Node.js
└── README.md
```

## Build APK

Memerlukan JDK 17 dan Android SDK 34.

```bash
cd telegram-web-client
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Hasil debug: `app/build/outputs/apk/debug/app-debug.apk`.

Tes hook badge:

```bash
node tools/badge_hook.test.js
```

## Catatan keamanan

- Metadata aplikasi dan hash PIN disimpan di `EncryptedSharedPreferences` dengan master key Android Keystore.
- Cookie dan storage Telegram dipisahkan per akun memakai AndroidX WebKit `MULTI_PROFILE` bila didukung versi Android System WebView perangkat.
- Storage Chromium/WebView **tidak dienkripsi oleh aplikasi secara terpisah**; lihat [dokumentasi klien](telegram-web-client/README.md) untuk batasan dan fitur yang belum tersedia.
- Signing release CI masih memakai keystore sementara; artifact CI belum cocok sebagai mekanisme upgrade stabil.
