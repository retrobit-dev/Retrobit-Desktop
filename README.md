# Retrobit Desktop

**Launcher Android native bergaya komputer retro dengan layar e-paper** — dibuat untuk tablet
(Huawei MatePad Mini, 8.7" 2560×1600, dukungan M-Pencil) tapi berjalan di perangkat Android apa pun
(Android 8.0+).

Bukan skin: ini launcher asli Android (`CATEGORY_HOME`) yang dibangun dengan **Kotlin + Jetpack Compose**,
lengkap dengan window manager, tiga gaya desktop yang bisa dipilih, dan sistem visual e-paper
(dithering, halftone, tanpa animasi mengganggu).

```
┌───────────────────────────────────────────────────────────────────────┐
│ ◆ retrOS  system  apps  term      [apps] [notes]  [LINUX]  88%  WIFI  │
├────┬──────────────────────────────────────────────────────────────────┤
│ ▤  │  ┌── terminal ─────────────────────────────┐   ┌── chrono ─────┐ │
│ ⌕  │  │ operator@retrobit:~$ neofetch           │   │   13:37       │ │
│ ▤  │  │   .-------------------.                 │   │ Mon, 13 Oct   │ │
│ ── │  │   |  | retR0BIT  _ |  |                 │   └───────────────┘ │
│ 📁 │  │   '-------------------'                 │   ┌── sysinfo ────┐ │
│ ⚙  │  └─────────────────────────────────────────┘   │ BAT 87% ▓▓▓▓  │ │
│    │                                                └───────────────┘ │
└────┴──────────────────────────────────────────────────────────────────┘
```

## Tampilan

![Pratinjau UI Retrobit Desktop](docs/ui-preview.png)

*Mockup dari palet, font, dan tata letak yang dipakai kode aslinya — dibuat ulang dengan
`tools/mockup.py`. Hasil di perangkat mengikuti kode Compose yang sama.*

## Fitur

### Tiga gaya desktop (bisa diganti kapan saja, dari pengaturan / terminal / chip di panel)

| Gaya | Ciri khas |
| --- | --- |
| **Linux** (`retrOS GNU/Linux`) | panel atas dengan menu sistem + task list, **side dock** di kiri, ikon grid, widget di kanan atas, **tiling window layout** opsional |
| **macOS** (`Retro System 9`) | **menu bar** atas (File/View/Window/Help), **dock** bawah dengan indikator jendela berjalan, **Launchpad**, **Spotlight** (⌘/meta atau tombol cari) |
| **Windows** (`Retrodows 95`) | **start menu** dua kolom, **taskbar** + system tray, ikon desktop bertumpuk di kiri, judul jendela bergradien dither khas Win95 |

### Sistem visual e-paper
- **6 palet**: Paper, Sepia, Tri-color (B&W + aksen merah), Ink (dark), Phosphor (hijau CRT), Amber.
  Perintah `invert` membalik kertas ⇄ tinta.
- **Wallpaper dither**: halftone dots, grid, stipple (Bayer 4×4), hatch, atau polos.
- **Ikon aplikasi dirender ulang**: `Color` (asli), `Mono` (grayscale ke ramp tinta→kertas),
  `1-bit` (halftone Bayer + outline, di-scale nearest-neighbour agar pixelnya tajam).
- Chrome: bevel tebal, bayangan *checker dither*, separator ber-*hatch*, judul jendela bergradien
  dither, marquee tanpa animasi, dan pola scanline halus.
- Semua ikon UI digambar sebagai **pixel-art 16×16** (glyph didefinisikan di dalam kode).

### Window manager
- Jendela bisa digeser (drag title bar), di-resize (grip kanan bawah), minimize / maximize / close.
- Gaya title bar mengikuti tema (traffic light macOS, gradient Win95, solid + tombol kanan Linux).
- Cascade otomatis, fokus, task list di panel/taskbar, dan **tiling** (master–stack) untuk gaya Linux.

### Input: pena, mouse, keyboard
- M-Pencil diperlakukan sebagai mouse: tap = klik kiri, **tap 2× = klik kanan**, barrel button = klik kanan.
- Kursor pointer e-paper untuk pena/mouse, drag langsung untuk memindahkan ikon dan jendela.
- Keyboard internal Android tetap berfungsi dan bisa dipanggil kapan saja lewat tombol ⌨ di tray
  (atau `Ctrl+Alt+K`); layout menyesuaikan tinggi keyboard.
- Dukungan keyboard eksternal dengan 10+ pintasan gaya desktop.

### Aplikasi bawaan
- **Terminal** (`rb-sh`) — 25+ perintah: `apps/ls`, `open <nama>`, `neofetch`, `df`, `free`, `ps`,
  `style linux|macos|windows`, `palette`, `wall`, `icons`, `scale`, `tiling on|off`, `invert`,
  `wmctrl`, `boot`, `help`, `exit`, dan peluncuran aplikasi langsung dengan mengetik namanya.
- **Applications** — app drawer dengan pencarian, long-press untuk menu, drag untuk pin.
- **Settings** — gaya desktop, palet, wallpaper, mode ikon, skala UI, **input & devices
  (pena/mouse/keyboard)**, perilaku, tombol "Set as default launcher".
- **System monitor** — baterai, RAM, penyimpanan, jaringan, uptime, daftar jendela, grafik tren.
- **Notes** — catatan teks yang tersimpan otomatis.
- **About** — info build, perangkat, dan daftar pintasan.

### Interaksi

| Aksi | Hasil |
| --- | --- |
| Tap ikon | jalankan aplikasi |
| Tap & tahan ikon | menu konteks (open, open in window, pin ke dock/desktop, app info, uninstall) |
| Tap & tahan lalu geser | drag ikon ke dock / sidebar untuk pin |
| Tap & tahan desktop | menu sistem (applications, terminal, settings, tiling, close all) |
| Tap judul jendela | jendela naik ke depan (click-to-focus) |

### M-Pencil / stylus sebagai mouse

Aplikasi ini memperlakukan M-Pencil seperti mouse, jadi tablet bisa dioperasikan persis seperti desktop:

| Aksi pen | Hasil |
| --- | --- |
| 1× tap | **klik kiri** — buka aplikasi / tekan tombol / pilih menu |
| 2× tap (dalam ±320 ms) | **klik kanan** — menu konteks pada ikon, desktop, atau area kosong |
| tombol barrel pada pena | klik kanan (dibaca sebagai tombol sekunder) |
| sentuh & geser dengan pena | drag langsung: pindahkan ikon, taruh di dock, geser/resize jendela |

- Tap pen tunggu ±320 ms sebelum "klik kiri" dijalankan — itu jeda standar desktop untuk membedakan
  klik tunggal dari klik ganda; tap jari tetap instan.
- Ada **kursor pointer e-paper** (panah 1-bit dengan halo) yang mengikuti posisi pena/mouse,
  membalik warna saat ditekan. Bisa dimatikan di Settings.
- Pen double-tap bisa dimatikan lewat *Settings → Input & devices → Pen double-tap = right click*.

### Mouse & keyboard

- **Mouse**: klik kiri = buka, klik kanan = menu konteks, scroll wheel = gulir daftar, hover memindahkan kursor.
- **Keyboard internal (bawaan Android) tetap dipakai**: muncul otomatis saat menyentuh field teks
  (terminal, notes, kotak pencarian), dan desktop bergeser naik mengikuti tinggi keyboard supaya
  baris input tidak tertutup. Ada tombol ⌨ di panel/taskbar/menu bar untuk memunculkan atau
  menyembunyikan keyboard secara manual — praktis kalau memakai mouse.
- **Keyboard eksternal** (Bluetooth/USB):

| Pintasan | Aksi |
| --- | --- |
| `Ctrl+Alt+T` / `Ctrl+Alt+S` | terminal / settings |
| `Ctrl+Alt+W` / `Ctrl+Alt+M` | tutup jendela aktif / minimize semua |
| `Ctrl+Alt+D` | menu desktop |
| `Ctrl+Alt+←` / `Ctrl+Alt+→` / `Tab` | pindah fokus antar jendela |
| `Ctrl+Alt+↑` | maximize/restore jendela aktif |
| `Ctrl+Alt+K` | munculkan / sembunyikan keyboard internal |
| `Super` (Meta) | Spotlight (cari aplikasi atau jalankan perintah shell) |
| `Esc` | tutup popup / spotlight / start menu |
| `Enter` | jalankan perintah di terminal & field satu baris |

### Boot sequence
Animasi BIOS singkat (bisa dimatikan di Settings) plus layar boot bergaya e-paper 1-bit.

## Instalasi di tablet

1. Unduh APK **release** dari salah satu sumber:
   - halaman **Releases** → tag `nightly` (selalu build terbaru), atau
   - tab **Actions** → run terakhir → artifact `retrobit-desktop-apk-*`.
2. Buka file APK di tablet, izinkan *Install unknown apps* untuk aplikasi pengelola file.
3. Tekan tombol **Home** → pilih **Retrobit Desktop** → pilih *Always*.
   Di tablet Huawei dengan gesture navigation: swipe up dari bawah, lalu pilih launcher ini.
4. Masuk ke **Settings → Launcher → Set as default launcher** agar bisa dicek dari dalam aplikasi.
5. Untuk kembali ke launcher bawaan: Android Settings → Apps → Default apps → Home app.

> Catatan Huawei: jika "Pure Mode"/mode ketat memblokir sideload, matikan pada
> *Settings → System & updates → Pure mode*, lalu instal ulang APK.
>
> Soal pena: M-Pencil terdeteksi sebagai stylus oleh Android, jadi semua fitur di atas bekerja tanpa
> setup tambahan. Kalau tablet memakai *glove mode*/*pen-only* mode dari Huawei, matikan dulu opsi
> tersebut supaya tap jari dan tap pena sama-sama dikenali.
> Semua data (palet, pin, catatan) disimpan lokal di perangkat; aplikasi tidak punya koneksi internet.

## Build & CI (GitHub Actions)

Setiap push menjalankan workflow [`.github/workflows/android.yml`](.github/workflows/android.yml):

1. checkout → JDK 17 → Android SDK (platform 35, build-tools 35.0.0) → Gradle (cache)
2. `./gradlew :app:assembleDebug :app:assembleRelease`
3. APK diunggah sebagai artifact: `retrobit-desktop-<run>-release.apk` dan `-debug.apk`
4. Saat dijalankan lewat **workflow_dispatch** (atau saat push tag `v*` / push ke `main`), APK
   dipublikasikan otomatis ke GitHub **Releases** (tag `nightly` untuk pre-release bergulir).
5. Kalau kompilasi gagal, error Kotlin/Gradle muncul sebagai **check annotations** + ringkasan job.

Build lokal:

```bash
./gradlew :app:assembleDebug      # butuh JDK 17 + Android SDK
```

### Signing

Agar update APK bisa di-install di atas instalasi sebelumnya, build CI selalu memakai keystore
yang sama: `keystore/retrobit-public.p12` (password `retrobit`, alias `retrobit`) — **keystore publik
untuk build sideload**, bukan rahasia. Untuk build pribadi, set `RB_KEYSTORE_FILE`,
`RB_KEYSTORE_PASSWORD`, `RB_KEY_ALIAS`, `RB_KEY_PASSWORD` dan skrip build akan memakainya.

## Struktur proyek

```
app/src/main/java/dev/retrobit/desktop/
├─ MainActivity.kt            # HOME activity, edge-to-edge, system bar + back handling
├─ data/                      # AppRepository (LauncherApps), Settings (SharedPreferences),
│                             # IconCache (render ikon e-paper), SystemInfo (baterai/RAM/disk/net)
├─ model/Models.kt            # DesktopStyle, PaletteId, WallPattern, IconMode, WinKind, AppEntry
├─ ui/theme/                  # Palette, font (VT323 + IBM Plex Mono), pola dither/Bayer
├─ ui/components/             # Chrome retro, glyph pixel-art, context menu, icon tile, gesture
├─ ui/wm/                     # WindowManager, WindowFrame, DragState
├─ ui/apps/                   # Terminal (rb-sh), Applications, Settings, System monitor, Notes, About
├─ ui/desktop/                # ShellLinux, ShellMacos, ShellWindows, widget, Spotlight, wallpaper
└─ ui/boot/BootScreen.kt      # animasi boot
```

## Font & lisensi

Seluruh antarmuka memakai **Pixel Operator** dan **Pixel Operator Mono** karya Jayvee Enaguas
(HarvettFox96), dilisensikan **CC0 1.0** (public domain) — teks lisensinya ada di
`licenses/PixelOperator-CC0.txt`. Font di-bundle di `app/src/main/res/font/`:

| File | Dipakai untuk |
| --- | --- |
| `pixel_operator.ttf` / `_bold` | judul, jam, judul jendela (`PixelDisplay`) |
| `pixel_operator_mono.ttf` / `_mono_bold` / `_mono_hb` | seluruh teks isi, terminal, tabel, progress bar (`PixelMono`) |

Dua catatan teknis penting:

1. **Ukuran teks dibulatkan ke grid 16 px.** Pixel Operator digambar pada em 16 px, jadi satu piksel
   glyph = satu piksel layar hanya kalau ukuran font yang dirender kelipatan 16 (16/32/48 px).
   Helper `pixelSp()` di `ui/components/Chrome.kt` membulatkan setiap permintaan ukuran teks ke grid
   itu — di tablet xxhdpi, 13sp menjadi 32 px. Ini yang membuat teks tetap tajam, bukan buram.
2. **String UI memakai ASCII.** Font ini tidak punya glyph box-drawing (─│┌┐), blok (█▓▒), panah
   (←→↑↓), atau simbol (◆⌘⌨). Semua teks aplikasi sudah diaudit dan diganti ke ASCII
   (`+---+`, `up/down/left/right`) supaya tidak jatuh ke font fallback dan merusak perataan tabel
   terminal. Skrip auditnya mudah diulang: baca cmap font lalu cek tiap literal string di `*.kt`.

Sisanya (ikon, glyph pixel-art 16×16, pola dither) digambar sendiri di dalam kode.
