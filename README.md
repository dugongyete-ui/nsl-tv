# NSL TV

Aplikasi IPTV Android pribadi (HP & TV Box) + panel uji coba stream berbasis web.

## 📦 download/ — APK Rilis Siap Pasang

**[NSL-TV-v1.0.2-release.apk](download/NSL-TV-v1.0.2-release.apk)** — build rilis (bukan debug), sudah zipalign + signed.
Bisa juga diunduh lewat panel web: tombol **Unduh APK** di header & kartu di sidebar (`GET /api/apk`).

## 📱 android/ — Aplikasi NSL TV (v1.0.2)

Native Android (Java + Media3/ExoPlayer), dibangun **tanpa Gradle** — cukup Android SDK build-tools + JDK 21.

Fitur:
- 196+ saluran live dari server upstream (endpoint & signature identik dengan skema klien resmi)
- Multi-sumber per saluran dengan **fallback otomatis** (1x retry → lompat sumber berikutnya) + watchdog 15 detik anti-macet
- DRM **Widevine** (server lisensi) & **ClearKey** (kunci lokal JWK/hex, sintesis PSSH common)
- Header anti-hotlink per saluran (user-agent/referer/origin) disuntik ke semua request manifest & segmen
- Picture-in-Picture, kualitas manual (AUTO/720p/480p…), ganti aspek FIT/CROP/STRETCH, drawer daftar saluran + pencarian
- Tanpa trial/lisensi — akses penuh tanpa batas

Cara build APK rilis:

```bash
cd android
bash build.sh
# hasil: out/NSL-TV-v1.0.2-release.apk (sudah zipalign + signed)
```

`build.sh` **portabel** — mendeteksi otomatis JDK (javac), `ANDROID_HOME`, build-tools 34, platform android-34,
dan memakai `d8.jar` bawaan build-tools (tanpa perlu unduh r8 terpisah).
Kebutuhan: Android SDK (build-tools 34 + platform android-34) dan JDK 17+.

> ⚠️ **Keystore rilis tidak ikut di-push** (keamanan). `build.sh` otomatis membuat keystore baru bila `nsl-release.keystore` tidak ditemukan — simpan keystore asli di tempat aman agar bisa update dengan tanda tangan yang sama.

## 🌐 web-tester/ — Panel Uji Coba Stream

Aplikasi Next.js untuk **menguji stream TV langsung di browser**:

- Sidebar daftar saluran (kategori + pencarian), 196 saluran
- Setiap sumber diuji otomatis: **manifest → variant → segmen** (hijau = pasti jalan)
- Pemutar HLS (hls.js) & DASH (dash.js) — termasuk **Widevine EME** dan **ClearKey** lokal
- Relay `/api/stream` menulis-ulang manifest & menyuntikkan header per saluran (menembus CORS/anti-hotlink)
- Relay `/api/license` untuk permintaan kunci Widevine
- Tombol **Unduh APK** mengambil APK rilis terbaru (label versi & ukuran dinamis dari `GET /api/apk?info=1`)
- Kartu unduh APK juga tersedia di **sidebar** (tetap terlihat di layar kecil)

Jalankan:

```bash
cd web-tester
bun install   # atau npm install
bun run dev   # buka http://localhost:3000
```

Endpoint panel: `GET /api/channels`, `GET /api/check`, `GET /api/stream`, `POST /api/license`, `GET /api/apk` (unduh, `?info=1` untuk metadata).

## Status uji (ringkas)

Dari 196 saluran: ±150 sumber pertama sehat dari sisi server; sisanya memang mati/blocked di upstream
(403 "Content rights blocked", 404, 502). Aplikasi & panel otomatis lompat ke sumber yang masih hidup.
