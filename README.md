# telegram-web

Klien Telegram Web untuk Android (WebView aman + tema Claymorphism).

## Struktur repo

```
.
├── .github/workflows/build-apk.yml   # CI: build APK release
├── prd/                               # PRD
├── telegram-web-client/               # Proyek Android (modul utama)
│   ├── app/                           # source + res
│   ├── gradle/wrapper/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradlew
└── README.md
```

## Cara build APK

```bash
cd telegram-web-client
./gradlew assembleDebug
# hasil: app/build/outputs/apk/debug/app-debug.apk
```

CI (`Build APK`) otomatis build release + upload artifact `telegram-web-apk`.

Detail fitur/keamanan: lihat [telegram-web-client/README.md](telegram-web-client/README.md).
