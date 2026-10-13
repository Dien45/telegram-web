# Telegram Web Client for Android

Aplikasi klien Telegram Web untuk Android dengan keamanan berlapis dan tema Claymorphism.

## Fitur Utama

- **Keamanan Berlapis**: Enkripsi AES-GCM, AndroidKeystore, biometric prompt
- **WebView Aman**: Whitelist domain, tanpa file access, mixed content disabled
- **Multi-Akun**: Isolasi profil per-akun dengan autentikasi PIN/biometrik
- **Anti-Root**: Peringatan keamanan (tidak memblokir)
- **Auto-Lock**: Kunci otomatis saat idle
- **Update Checker**: Pemeriksaan update GitHub Releases dengan verifikasi SHA-256
- **Download Manager**: Unduhan aman ke external storage
- **Proxy Support**: Proxy per-akun (opsional, terenkripsi)
- **Telemetri Opt-In**: Data penggunaan dikirim ke server self-hosted (opsional)
- **Tema Claymorphism**: Warna biru muda dengan aksen pink muda

## Struktur Proyek

```
telegram-web-client/
├── app/
│   ├── src/main/
│   │   ├── java/com/telegram/webclient/
│   │   │   ├── BootReceiver.kt
│   │   │   ├── DownloadManager.kt
│   │   │   ├── LockActivity.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── SecureJsBridge.kt
│   │   │   ├── SecureWebChromeClient.kt
│   │   │   ├── SecureWebViewClient.kt
│   │   │   ├── TelegramApp.kt
│   │   │   └── ui/account/AccountViewModel.kt
│   │   ├── res/
│   │   │   ├── drawable/
│   │   │   │   ├── clay_button_background.xml
│   │   │   │   └── clay_button_background_dark.xml
│   │   │   ├── layout/
│   │   │   │   ├── activity_lock.xml
│   │   │   │   ├── activity_main.xml
│   │   │   │   └── splash/
│   │   │   │       ├── SplashScreen.xml
│   │   │   │       └── SplashScreen.Dark.xml
│   │   │   └── values/
│   │   │       ├── colors.xml
│   │   │       ├── strings.xml
│   │   │       └── styles.xml
│   │   └── build.gradle.kts
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── README.md
```

## Cara Build APK

1. Install Android Studio atau Android SDK command-line tools
2. Pastikan `JAVA_HOME` dan `ANDROID_HOME` sudah diatur
3. Jalankan di terminal:
   ```bash
   cd telegram-web-client
   ./gradlew assembleDebug
   ```
4. APK akan dihasilkan di `app/build/outputs/apk/debug/app-debug.apk`

## Keamanan

- Enkripsi AES-GCM untuk data lokal
- AndroidKeystore untuk master key
- BiometricPrompt untuk autentikasi
- Whitelist domain (hanya web.telegram.org dan k.telegram.org)
- Tidak ada akses file:// ke external storage
- Mixed content disabled
- HTTPS-only

## Lisensi

MIT License