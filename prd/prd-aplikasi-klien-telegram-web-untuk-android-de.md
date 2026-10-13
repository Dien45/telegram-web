# PRD: Aplikasi Klien Telegram Web untuk Android dengan Keamanan Berlapis## RingkasanMembangun aplikasi Android yang membungkus (wrap) Telegram Web (WebK/WebA) dalam sebuah klien native yang dikeraskan keamanannya, sehingga pengguna bisa mengakses Telegram lewat antarmuka web yang ringan namun dengan proteksi tambahan yang tidak dimiliki browser biasa: kunci biometrik aplikasi, penyimpanan sesi terisolasi, auto-lock idle, **enkripsi data lokal**, **dukungan multi-akun**, **proxy opsional**, dan kontrol privasi jaringan. Fitur pemblokiran screenshot **tidak** termasuk dalam cakupan, sehingga pengguna tetap dapat mengambil screenshot seperti biasa. Distribusi dilakukan lewat **GitHub Releases (sideload)** dan **build dari source**, sehingga tidak ada ketergantungan pada kebijakan Play Console.## AsumsiKarena ide awal singkat, berikut asumsi yang dipakai. Bila berbeda, dokumen ini perlu direvisi: **Basis aplikasi**: klien native Android (Kotlin) yang memuat Telegram Web melalui `WebView` yang dikonfigurasi ketat, bukan reimplementasi protokol MTProto dari nol. **Akun**: pengguna login dengan akun Telegram yang sudah ada (nomor telepon + kode OTP, atau QR login). Aplikasi tidak menyimpan kredensial Telegram ke server pihak ketiga. **Distribusi**: sideload GitHub Releases atau build dari source. Tidak ada Google Play Store, sehingga tidak ada Play policy review, Play Console, atau Crashlytics bawaan Play. **Telemetri**: self-hosted (Umami/PostHog self-host) dan/atau opt-in saja. **Target perangkat**: Android 8.0 (API 26) ke atas, fokus pada perangkat mid-range dan hemat baterai. **Pembuat**: pengembang individu/tim kecil dengan siklus rilis cepat. **"Keamanan mempuni"** diinterpretasikan sebagai: hardening klien, privasi sesi, enkripsi lokal, verifikasi integritas build, **bukan** jaminan keamanan end-to-end Telegram itu sendiri (yang sudah dijamin MTProto di sisi Telegram). **Screenshot diizinkan**: aplikasi **tidak** mengaktifkan `FLAG_SECURE` dan **tidak** memblokir screenshot maupun screen recording.## Latar Belakang & MasalahAplikasi Telegram resmi untuk Android sudah aman, tetapi berat, penuh fitur yang tidak semua orang butuh, dan tetap saja ada celah praktis level perangkat: 1. **Sesi mudah diakses setelah unlock layar** — siapa pun yang memegang perangkat yang sudah terbuka bisa membaca chat. Aplikasi resmi punya kunci PIN/passcode, tetapi tidak semua pengguna mengaktifkannya dan tidak semua klien pihak ketiga punya.
2. **Isolasi sesi lemah** — banyak klien berbasis WebView berbagi cookie/storage dengan browser atau antar-aplikasi, sehingga sesi Telegram bisa bocor lewat XSS, ekstensi, atau proses lain.
3. **Data lokal tidak terenkripsi** — cache dan sesi tersimpan dalam bentuk yang mudah dibaca bila perangkat di-root atau diakses fisik.
4. **Tidak ada kontrol jaringan** — pengguna tidak bisa memaksa koneksi lewat jalur tertentu (mis. VPN/proxy) atau memblokir cleartext.
5. **Distribusi kepercayaan** — pengguna yang sadar privasi enggan memasang aplikasi dari sumber tak jelas, dan tidak bisa memverifikasi bahwa APK yang diunduh benar-benar sesuai source.
6. **Kebutuhan ringan** — sebagian pengguna menginginkan klien Telegram yang hemat RAM/baterai dibanding aplikasi resmi yang berat. Telegram Web sendiri sudah tersedia, tetapi dipakai lewat browser yang tidak dikeraskan: tidak ada kunci biometrik, tidak ada isolasi storage, tidak ada enkripsi lokal, tidak ada kontrol jaringan. Aplikasi ini menutup celah tersebut. Sementara itu, kebutuhan mengambil screenshot (mis. menyimpan info penting, membagikan tangkapan layar ke rekan kerja) tetap dipertahankan sebagai fungsi normal.## Tujuan (Goals) dan Bukan Tujuan (Non-Goals)### TujuanMenyediakan klien Telegram berbasis Telegram Web untuk Android yang **ringan** (RAM dan ukuran APK kecil) dan **aman secara praktis** level perangkat. Menambahkan lapisan keamanan yang tidak dimiliki browser: kunci biometrik/PIN aplikasi, isolasi storage, **enkripsi penyimpanan lokal**, auto-lock idle, dan kontrol jaringan. Mendukung **multi-akun** dalam satu instalasi dengan isolasi data antar-akun. Menyediakan **dukungan proxy** (SOCKS5/HTTP) sebagai jalur koneksi opsional. Menyediakan distribusi transparan lewat GitHub Releases dengan **verifikasi integritas build** (hash/signature) dan opsi build dari source. Menjaga kompatibilitas dengan Telegram Web resmi agar tidak merusak perilaku login/chat saat Telegram memperbarui web-nya. Mempertahankan fungsi screenshot normal tanpa pembatasan dari aplikasi.### Bukan TujuanMembuat klien Telegram native dengan implementasi MTProto sendiri. Menjadi pengganti penuh aplikasi Telegram resmi (fitur seperti panggilan video, story, atau bot developer tools tidak menjadi prioritas). Menyediakan server proxy/relay milik sendiri — proxy hanya sebagai klien yang menghubungkan ke server milik pengguna. Mendukung iOS, desktop, atau Web. Menyimpan pesan pengguna di server pihak mana pun. **Memblokir screenshot atau screen recording** secara eksplisit luar cakupan.## Target Pengguna / Persona
| Persona | Deskripsi | Kebutuhan utama |
|---|---|---|
| **Rian, 28, pekerja kantoran** | Pakai Telegram untuk kerja & komunitas, HP sering dipinjam rekan | Kunci biometrik cepat, auto-lock, sesi terisolasi |
| **Sari, 34, aktivis/peneliti** | Peduli privasi, pakai VPN/proxy, tidak mau pasang dari Play Store | Kontrol jaringan (force VPN, proxy), verifikasi APK, build dari source |
| **Budi, 22, mahasiswa** | HP mid-range RAM 3 GB, baterai cepat habis | Klien ringan, hemat baterai, ukuran APK kecil |
| **Dewi, 41, orang tua** | Tidak teknis, butuh sederhana | Login mudah (QR/OTP), tidak ada setup rumit, auto-update jelas |
| **Andi, 25, content creator** | Sering screenshot chat untuk konten/arsip | Screenshot tetap bisa dilakukan tanpa hambatan |
| **Fajar, 30, freelancer** | Punya akun kerja & pribadi terpisah | Multi-akun dengan isolasi data antar-akun |

## User Stories
- Sebagai pengguna baru, saya ingin login dengan nomor telepon/QR seperti Telegram biasa, supaya saya tidak perlu membuat akun baru atau mengubah kebiasaan.
- Sebagai pengguna, saya ingin mengunci aplikasi dengan biometrik/PIN, supaya orang lain yang memegang perangkat saya tidak bisa membaca chat.
- Sebagai pengguna, saya ingin aplikasi otomatis terkunci setelah beberapa menit idle, supaya sesi tidak terbuka saat ditinggal.
- Sebagai pengguna, saya ingin sesi Telegram tersimpan terisolasi (tidak berbagi cookie dengan browser), supaya sesi tidak bocor lewat aplikasi/proses lain.
- Sebagai pengguna, saya ingin data lokal (sesi, cache, kunci) terenkripsi, supaya data tidak bisa dibaca bila perangkat di-root atau diakses fisik.
- Sebagai pengguna dengan akun kerja & pribadi, saya ingin menambahkan dan berpindah antar beberapa akun dalam satu aplikasi, supaya saya tidak perlu install dua aplikasi.
- Sebagai pengguna, saya ingin setiap akun terisolasi datanya, supaya logout satu akun tidak mengganggu akun lain dan tidak ada kebocoran antar-akun.
- Sebagai pengguna sadar privasi, saya ingin mengonfigurasi proxy (SOCKS5/HTTP) untuk koneksi Telegram, supaya saya bisa mengontrol jalur lalu lintas.
- Sebagai pengguna sadar privasi, saya ingin memaksa semua koneksi lewat VPN dan memblokir cleartext, supaya lalu lintas tidak bocor di jaringan publik.
- Sebagai pengguna sadar privasi, saya ingin memverifikasi hash/signature APK terhadap source, supaya saya yakin build tidak dimodifikasi.
- Sebagai pengguna, saya ingin aplikasi memberi tahu saat ada versi baru GitHub Releases, supaya saya bisa update manual dengan aman.
- Sebagai pengguna mid-range, saya ingin aplikasi hemat RAM/baterai, supaya tidak mengganggu pemakaian sehari-hari.
- Sebagai pengguna, saya ingin logout dengan **wipe eksplisit** yang menghapus semua data lokal akun tersebut (cookie, cache, kunci, enkripsi), supaya tidak ada sisa data saat perangkat dijual.
- Sebagai pengguna, saya ingin tetap bisa mengambil screenshot chat kapan saja, supaya saya bisa menyimpan atau membagikan tangkapan layar tanpa hambatan.
- Sebagai pengguna, saya ingin aplikasi memberi **peringatan** bila dijalankan di perangkat yang ter-root atau lingkungan tidak aman, supaya saya sadar risikonya tanpa dipaksa keluar.

## Functional Requirements
Prioritas: **P0** wajib untuk rilis pertama, **P1** penting, **P2** nice-to-have.

| # | Requirement | Prioritas | Kriteria uji |
|---|---|---|---|
| FR-1 | Aplikasi memuat Telegram Web resmi di dalam `WebView` yang dikonfigurasi ketat (JavaScript hanya dari domain Telegram, tanpa `addJavascriptInterface` yang tidak perlu, `setAllowFileAccess(false)`). | P0 | Halaman Telegram Web termuat; tidak ada error konsol terkait kebijakan. |
| FR-2 | Login via nomor telepon (OTP) dan/atau QR berfungsi seperti di Telegram Web. | P0 | Pengguna berhasil masuk dan melihat daftar chat. |
| FR-3 | Kunci aplikasi dengan biometrik (BiometricPrompt) dan fallback PIN 6 digit. | P0 | Aplikasi meminta autentikasi saat dibuka; salah PIN 5x memicu lockout. |
| FR-4 | Auto-lock setelah idle N menit (default 1 menit, dapat diatur 15 detik–30 menit). | P0 | Setelah idle, aplikasi kembali ke layar kunci. |
| FR-5 | Sesi & cookie Telegram disimpan di penyimpanan terisolasi milik aplikasi (CookieManager privat, tanpa shared storage). | P0 | Cookie tidak terbaca oleh aplikasi lain; logout menghapus semua. |
| FR-6 | **Enkripsi data lokal**: sesi, cache, dan data sensitif disimpan terenkripsi (kunci di Android Keystore; enkripsi at-rest, mis. SQLCipher/EncryptedFile). | P0 | Data mentah di direktori aplikasi tidak dapat dibaca tanpa kunci; verifikasi dengan inspeksi file. |
| FR-7 | **Multi-akun**: pengguna dapat menambah, berpindah, dan menghapus beberapa akun Telegram dalam satu instalasi. | P0 | Berpindah akun cepat (< 3 detik); tiap akun punya storage/cookie terpisah. |
| FR-8 | **Isolasi antar-akun**: setiap akun memakai profil WebView/cookie store sendiri; logout satu akun tidak memengaruhi akun lain. | P0 | Data akun A tidak terlihat di sesi akun B; logout A tidak mengubah B. |
| FR-9 | **Wipe eksplisit saat logout**: tombol logout menghapus cookie, cache, WebView data, kunci enkripsi, dan file lokal akun tersebut secara menyeluruh. | P0 | Setelah logout, tidak ada sisa file/data akun (diverifikasi inspeksi direktori). |
| FR-10 | Aplikasi **tidak** mengaktifkan `FLAG_SECURE`; screenshot dan screen recording berjalan normal. | P0 | Screenshot berhasil tersimpan; tidak ada layar hitam. |
| FR-11 | **Dukungan proxy**: konfigurasi SOCKS5/HTTP (host, port, kredensial opsional) untuk koneksi Telegram Web. | P1 | Dengan proxy valid, koneksi berhasil; proxy tidak valid menampilkan error jelas. |
| FR-12 | Kontrol jaringan: opsi "Force VPN" dan blokir cleartext (hanya HTTPS). | P1 | Dengan VPN mati + opsi aktif, aplikasi menolak memuat. |
| FR-13 | Update checker ke GitHub Releases API: cek versi terbaru, tampilkan changelog, unduh APK. | P1 | Notifikasi muncul saat ada rilis baru; unduhan berhasil. |
| FR-14 | Verifikasi integritas: tampilkan SHA-256 APK dan tautan signature/checksums dari rilis. | P1 | Hash APK cocok dengan yang dipublikasikan. |
| FR-15 | Pengaturan privasi: durasi auto-lock, force VPN, konfigurasi proxy, mode anti-root. | P1 | Setiap perubahan langsung berlaku dan tersimpan. |
| FR-16 | **Peringatan perangkat root/lingkungan tidak aman** (hanya peringatan, tidak memaksa keluar). | P1 | Pada perangkat root, muncul dialog peringatan dengan opsi "Lanjutkan". |
| FR-17 | Dukungan tema gelap/terang mengikuti sistem dan tampilan edge-to-edge. | P2 | UI menyesuaikan tema sistem tanpa glitch. |
| FR-18 | Penanganan tautan eksternal: buka di browser kustom internal atau browser default, bukan di WebView utama. | P2 | Tautan luar tidak merusak sesi Telegram Web. |
| FR-19 | Telemetri opt-in (self-hosted) untuk crash & penggunaan anonim, default **mati**. | P2 | Tidak ada data terkirim bila pengguna tidak menyetujui. |
| FR-20 | **Badge chat belum terbaca**: tampilkan jumlah chat belum dibaca sebagai badge native di ikon aplikasi (ala Telegram), disinkronkan dari document.title Telegram Web via JS bridge. | P1 | Badge muncul/hilang sesuai jumlah unread; nol membersihkan badge. |

## Non-Functional Requirements
**Performa**: cold start < 2,5 detik perangkat mid-range; pemakaian RAM idle < 150 MB; ukuran APK < 15 MB. Enkripsi/dekripsi tidak boleh menambah latensi signifikan pada operasi UI (< 100 ms per operasi umum).

**Baterai**: tidak ada wakelock persisten; auto-lock memakai timer ringan; konsumsi baterai setara atau lebih baik dari browser.

**Keamanan**: Tidak ada `addJavascriptInterface` objek sensitif; validasi origin sebelum memuat konten. Certificate pinning opsional untuk domain Telegram (dapat dimatikan bila Telegram mengganti sertifikat). Kunci enkripsi lokal disimpan di Android Keystore (bukan SharedPreferences plaintext). Tidak ada logging data sensitif (token, isi pesan) di logcat pada build rilis. APK rilis ditandatangani dan hash dipublikasikan. Proxy dikonfigurasi pengguna dan kredensial proxy (jika ada) disimpan terenkripsi. Screenshot diizinkan secara desain, sehingga aplikasi **tidak** mengklaim perlindungan terhadap pengambilan tangkapan layar oleh pengguna.

**Privasi**: tidak ada pengumpulan data tanpa opt-in; tidak ada SDK iklan; tidak ada pihak ketiga selain Telegram, proxy milik pengguna, dan (opsional) endpoint telemetri self-hosted.

**Kompatibilitas**: Android 8.0–15; mengikuti perubahan Telegram Web (pemantauan berkala).

**Reliabilitas**: penanganan kegagalan muat WebView dengan tombol retry; tidak crash saat jaringan terputus; kegagalan proxy menampilkan pesan yang dapat ditindaklanjuti.

**Maintainability**: kode open source, build reproducible (dokumentasi langkah build) untuk memverifikasi hash.

**Aksesibilitas**: kontras memadai, dukungan TalkBack pada layar kunci dan pengaturan.

## Success Metrics / KPI
| Metrik | Target (6 bulan pertama) |
|---|---|
| Unduhan APK dari GitHub Releases | ≥ 5.000 |
| Retensi D30 (pengguna yang masih membuka aplikasi) | ≥ 30% |
| Tingkat aktivasi kunci biometrik/PIN | ≥ 60% dari pengguna aktif |
| Pengguna yang memakai fitur multi-akun | ≥ 20% dari pengguna aktif |
| Crash-free sessions | ≥ 99% |
| Ukuran APK | < 15 MB |
| Cold start (P90, mid-range) | < 2,5 detik |
| Waktu penyelesaian update setelah rilis Telegram Web breaking | < 7 hari |
| Laporan kebocoran sesi/insiden keamanan | 0 |

## Milestone & Timeline (draft)
| Fase | Fokus | Estimasi |
|---|---|---|
| **M0 — Riset & PoC** | Validasi WebView + Telegram Web, cek login/QR, uji isolasi cookie, uji enkripsi lokal & profil WebView multi-akun | 2–3 minggu |
| **M1 — MVP (P0)** | FR-1 s/d FR-10: WebView terhardening, login, kunci biometrik/PIN, auto-lock, isolasi sesi, enkripsi lokal, multi-akun + isolasi, wipe eksplisit, screenshot normal | 6–8 minggu |
| **M2 — Keamanan & Distribusi (P1)** | FR-11 s/d FR-20: dukungan proxy, kontrol jaringan, update checker GitHub, verifikasi hash, layar pengaturan, peringatan root | 4–5 minggu |
| **M3 — Polish (P2)** | FR-17 s/d FR-19: tema, penanganan tautan, telemetri opt-in, aksesibilitas | 3 minggu |
| **M4 — Rilis Publik & Iterasi** | Rilis GitHub Releases v1.0, dokumentasi build, CI reproducible, pemantauan kompatibilitas Telegram Web | Berkelanjutan |

## Risiko & Pertanyaan Terbuka
### Risiko
1. **Perubahan Telegram Web mematahkan klien** — Telegram Web bisa mengubah struktur/DOM atau kebijakan embed kapan saja. *Mitigasi*: minimalkan ketergantungan pada internal DOM, gunakan hanya URL resmi, siapkan rilis cepat, pantau changelog.
2. **Multi-akun + enkripsi menambah kompleksitas** — isolasi storage per akun dan manajemen kunci enkripsi bisa rawan bug dan menambah waktu pengembangan. *Mitigasi*: rancang model data per-akun sejak awal, uji isolasi secara eksplisit, gunakan profil WebView terpisah.
3. **Enkripsi lokal tidak melindungi saat aplikasi terbuka** — enkripsi at-rest hanya melindungi data saat aplikasi terkunci/perangkat mati; saat aplikasi berjalan, data dapat diakses. *Mitigasi*: kombinasikan dengan auto-lock singkat dan kunci biometrik; komunikasikan batas ini.
4. **Screenshot diizinkan → data lebih mudah bocor** — karena `FLAG_SECURE` tidak dipakai, siapa pun yang memegang perangkat terbuka bisa memotret chat. *Mitigasi*: perkuat auto-lock idle yang singkat dan kunci biometrik; komunikasikan trade-off ini secara jujur.
5. **Proxy menambah permukaan risiko** — proxy pihak ketiga bisa melakukan MITM bila tidak tepercaya; kredensial proxy bisa bocor. *Mitigasi*: simpan kredensial terenkripsi, peringatkan pengguna untuk hanya memakai proxy tepercaya, sediakan opsi verifikasi sertifikat.
6. **Certificate pinning rapuh** — rotasi sertifikat Telegram bisa membuat aplikasi gagal konek. *Mitigasi*: jadikan opsional, sediakan jalur fallback, dokumentasikan.
7. **Kebijakan Telegram terhadap klien pihak ketiga** — penggunaan Telegram Web dalam WebView pihak ketiga dapat dianggap melanggar ToS. *Mitigasi*: tinjau ToS Telegram, jangan memodifikasi/menyalahgunakan API, siap hentikan distribusi bila diminta.
8. **Distribusi sideload** — pengguna harus mengaktifkan "Install unknown apps"; risiko APK palsu beredar. *Mitigasi*: publikasikan hash signature, dokumentasi verifikasi, hanya rilis dari repo resmi.
9. **Isolasi sesi tidak sempurna** — WebView tetap berbagi proses dengan aplikasi; XSS konten web tetap mungkin. *Mitigasi*: batasi JavaScript origin Telegram, nonaktifkan file access, validasi navigasi.
10. **Beban pemeliharaan** — proyek kecil dengan perubahan web cepat bisa membuat proyek terbengkalai. *Mitigasi*: arsitektur sederhana, otomatisasi, dokumentasi jelas.

### Pertanyaan Terbuka
- Apakah proyek ini akan **open source penuh** (agar klaim "build dari source" dan verifikasi hash bermakna)? Jika tidak, verifikasi integritas kehilangan sebagian nilainya.
- Berapa **jumlah maksimum akun** yang didukung (mis. 2, 5, tak terbatas)? Ini memengaruhi desain storage dan memori.
- Apakah enkripsi lokal mencakup **seluruh cache WebView** (yang dikelola Chromium) atau hanya data aplikasi sendiri? Cache WebView sulit dienkripsi penuh tanpa kontrol penuh atas Chromium.
- Apakah proxy harus mendukung **per-akun** (akun A lewat proxy X, akun B langsung), atau satu konfigurasi global?
- Apakah mode anti-root hanya **peringatan** (sesuai keputusan saat ini) atau nanti berkembang menjadi pembatasan fitur tertentu?
- Bagaimana kebijakan **penghapusan data saat uninstall** — cukup mengandalkan Android, atau perlu mekanisme tambahan? (Wipe eksplisit saat logout sudah diputuskan, tetapi uninstall di luar kendali aplikasi.)
- Apakah aplikasi harus menolak berjalan **tanpa VPN** (mode ketat) atau hanya memperingatkan? (Saat ini diarahkan ke peringatan saja.)

### Catatan: Apa Itu Mode Kiosk?
**Mode kiosk** (kiosk mode / single-app mode) adalah mode di mana perangkat Android dikunci agar **hanya bisa menjalankan satu aplikasi saja**, dan pengguna tidak bisa keluar dari aplikasi itu, mengakses home screen, membuka notifikasi, atau berpindah ke aplikasi lain tanpa kredensial admin. Biasanya dipakai untuk perangkat yang dipakai bersama di tempat publik (tablet resepsionis, mesin absensi, display toko) atau perangkat kerja khusus.

Relevansinya untuk proyek ini: jika aplikasi Telegram Web kamu dijalankan dalam mode kiosk, maka perangkat hanya bisa dipakai untuk Telegram — cocok misalnya untuk perangkat kerja yang hanya boleh mengakses Telegram, atau perangkat yang ditinggal di lokasi tertentu. Namun mode kiosk umumnya **tidak relevan** untuk penggunaan pribadi sehari-hari karena justru membatasi kebebasan pengguna. Karena itu pada PRD ini mode kiosk **tidak dimasukkan** sebagai fitur; jika nanti dibutuhkan (mis. untuk skenario perangkat khusus), bisa ditambahkan sebagai fitur P2 terpisah dengan dukungan Android Device Owner / Lock Task Mode.