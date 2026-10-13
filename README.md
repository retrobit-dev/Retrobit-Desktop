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
- **Wallpaper**: tiga sumber, semuanya berakhir sebagai e-paper.
  - *Pattern* — enam pola dither bawaan (halftone dots, grid, stipple Bayer 4×4, hatch, polos).
  - *Device* — wallpaper tablet kamu, dibaca lewat WallpaperManager, di-crop, diturunkan resolusinya
    ke ~260 sel lalu di-dither ulang (satu sel = satu titik tinta/kertas).
  - *Image* — gambar apa pun dari penyimpanan (Android Photo Picker). Pilihanmu disimpan sebagai
    URI persistable.
  Tiga **tone** untuk sumber gambar: `Halftone` (titik rapat), `Gray` (garis scanning halus),
  `Soft` (blok besar, kontras rendah). Warna aslinya dibuang — hanya kertas, tinta, dan abu-abu.
- **Ikon aplikasi dirender ulang**: `Color` (asli), `Mono` (grayscale ke ramp tinta→kertas),
  `1-bit` (halftone Bayer + outline, di-scale nearest-neighbour agar pixelnya tajam).
- **Ikon mudah dikenali**: bingkai ikon diwarnai **accent palet** (merah di tri-color, hijau di
  phosphor, kuning di amber), ada **plat huruf pertama** aplikasi di sudut ikon supaya aplikasi
  dengan ikon mirip tetap bisa dibedakan, dan **chip nama** tercetak di bawah tiap ikon desktop.
  Ketiganya bisa dimatikan di *Settings - app icon readability*.
- **Bentuk bingkai ikon mengikuti tema**: `Auto` (Linux → square flat, macOS → rounded, Windows →
  bevel klasik), atau pilih manual: `Classic`, `Rounded`, `Flat`, `Plain`. Cache ikon otomatis
  dibuang saat tema/bentuk berubah.
- **Jam menampilkan detik** (opsional, `Settings` atau `seconds on|off`) — jam di panel, taskbar,
  dan widget ikut berubah.
- Chrome: bevel tebal, bayangan *checker dither*, separator ber-*hatch*, judul jendela bergradien
  dither, marquee tanpa animasi, dan pola scanline halus.
- Semua ikon UI digambar sebagai **pixel-art 16×16** (glyph didefinisikan di dalam kode).

### Aplikasi terbuka di dalam jendela (floating apps)
- **Setiap aplikasi yang dibuka dari launcher mendapat jendela desktop**: punya title bar dengan
  nama + ikon aplikasi, tombol minimize / maximize / close, bisa digeser, di-resize dari 8 arah,
  di-snap ke tepi, dipindah antar-workspace, dan muncul di taskbar / task list / `ps`.
- **Floating window sungguhan di tablet yang mendukung**: `data/WindowedLaunch.kt` memeriksa fitur
  `android.software.freeform_window_management`, lalu mengirim aplikasi ke persis segi empat jendela
  kita (`ActivityOptions.setLaunchBounds` via `LauncherApps.startMainActivity`). Aplikasi berjalan
  mengapung di atas desktop, dan tombol **place here** memindahkannya saat jendela digeser/di-resize.
- **Kalau perangkat tidak mendukung**: aplikasi terbuka penuh (seperti launcher biasa), sementara
  jendela tetap menjadi "wakil"-nya di taskbar, daftar jendela dan workspace — plus tombol
  **open app / bring it back**. Status perangkat ditampilkan di *Settings - apps & windows*.
  Cara mengaktifkan floating window: *developer options* → *force activities to be resizable*, atau
  `adb shell settings put global enable_freeform_support 1`.
- Android sendiri **tidak mengizinkan** satu aplikasi menggambar UI aplikasi lain di dalam view-nya
  (`android.app.ActivityView` sudah dihapus dari SDK setelah API 29 dan bukan API publik), jadi
  inilah jalur yang benar: chrome milik kita + jendela sistem milik aplikasi.
- Matikan lewat *Settings - apps & windows* atau terminal: `floating off` (semua aplikasi jadi
  full screen lagi). Menu konteks ikon aplikasi juga punya **Open (floating window)** dan
  **Open full screen**.

### Kalender & widget desktop
- **Semua widget lebarnya sama dengan widget kalender** (250 dp) supaya kolomnya rapi, dan
  **ukurannya bisa diatur manual**: tarik tepi kanan (lebar), tepi bawah (tinggi), atau sudut
  kanan-bawah (keduanya). Ukuran di-snap ke grid 8 px, minimum 160x56 dp, dan diingat per widget
  (`Settings - display widgets`, terminal `widgets`, `widget width 300`, `widget size calendar 280 420`,
  `widget reset`). Tinggi 0 = setinggi isinya; kalau lebih pendek, isi widget bisa di-scroll.
  Matematikanya murni di `ui/wm/WidgetMath.kt` (9 unit test).
- **Widget kalender** di desktop: grid bulan, penanda titik pada hari yang ada acaranya, panel
  agenda hari terpilih, dan tombol `+` untuk menambah entri **langsung dari desktop**.
- Empat jenis entri: **event**, **task**, **agenda**, dan **birthday** (birthday otomatis berulang
  tiap tahun). Task punya kotak centang `[ ]`/`[x]`.
- **Jendela Calendar** (chip `Calendar`, Spotlight "Calendar", terminal `open calendar`) berisi
  tampilan bulan besar, daftar agenda 14 hari ke depan, daftar task terbuka, dan daftar birthday.
- Terminal: `cal` (agenda 14 hari), `cal add "Temu dokter" 2026-11-02 14:30 task`,
  `cal tasks`, `cal clear`.
- Semua entri disimpan lokal di SharedPreferences (`retrobit_calendar`) sebagai JSON — tanpa izin,
  tanpa akun, tanpa jaringan.

### Aman untuk kamera & sudut melengkung
- Tablet modern punya **sudut melengkung** dan **punch-hole kamera**. Launcher menggambar sampai
  tepi layar, tapi panel, dock, widget, ikon dan jendela digeser masuk sejauh
  `max(cutout, radius sudut)` — jadi tidak ada yang tertutup kamera atau terpotong lengkungan.
- Cutout dibaca langsung dari window insets (`safeInsetLeft/Top/Right/Bottom`), jadi posisi kamera
  mengikuti perangkat. Radius sudut bisa dipilih di *Settings - display corners & camera hole*
  (`flat`, `12dp`, `20dp`, `28dp`) atau lewat terminal: `safearea on|off|20`.

### Window manager
- Jendela bisa digeser (drag title bar), di-resize (grip kanan bawah), minimize / maximize / close.
- Gaya title bar mengikuti tema (traffic light macOS, gradient Win95, solid + tombol kanan Linux).
- Cascade otomatis, fokus, task list di panel/taskbar, dan **tiling** (master–stack) untuk gaya Linux.
- **Snap ke tepi layar**: geser jendela ke tepi kiri/kanan → menempati separuh layar, ke tengah-atas →
  maximize. Muncul pratinjau berupa outline garis putus-putus (dither) sebelum kamu melepasnya.
  Sudut atas-kiri/kanan tetap mengikuti aturan tepi (kiri/kanan), seperti Aero Snap.
- **Virtual desktop (workspace)**: 1–4 desktop, jendela hidup per-desktop. Pager berupa kotak angka
  di panel / menu bar / taskbar: tap untuk pindah, titik di dalamnya menunjukkan jumlah jendela,
  tekan lama untuk menu desktop (pindah jendela atau ubah jumlah desktop).
- **Semua sisi jendela bisa di-resize manual**: delapan handle (kiri, kanan, atas, bawah, dan empat
  sudut) yang bisa ditarik pakai jari, M-Pencil maupun mouse. Ukuran minimum dijaga, jendela tidak
  bisa keluar dari desktop, dan ukuran hidup (`820x640`) tampil di title bar saat ditarik.
  Double-tap title bar = maximize/restore.
- **Floating windows**: setiap jendela mengambang dan punya tombol dua-kotak di title bar untuk
  keluar-masuk dari layout tiling. Jendela mengambang mempertahankan ukuran yang kamu beri, jendela
  lain mengikuti tiling master-stack.
- **Ikon dock / sidebar / quick launch bisa diatur urutannya**: tarik ikon ke sepanjang dock, muncul
  penanda garis di slot tujuan. Tarik ikon keluar ke desktop untuk memindahkannya dari dock ke grid,
  atau dari grid ke dock — dua arah.
- **Ikon desktop bisa diletakkan di mana saja**: tiap ikon punya sel sendiri di grid tetap
  (10×5 di tablet), disimpan per-paket. Lepas ikon di sel berisi → kedua ikon **bertukar tempat**,
  jadi tidak ada yang terdorong keluar layar. Menu desktop → *Auto arrange icons* untuk kembali
  ke susunan otomatis, *Show placement grid* untuk memunculkan garis pandu saat men-drag.
- Matematika layout dipisah ke file murni Kotlin tanpa Android supaya bisa di-unit-test:
  `ui/wm/LayoutMath.kt` (snap, tiling, **resize**), `ui/wm/GridMath.kt` (grid ikon) dan
  `ui/wm/DockMath.kt` (urutan dock) dan `ui/wm/WidgetMath.kt` (ukuran widget) — **56 test**:
  23 `LayoutMathTest` + 15 `GridMathTest` + 9 `DockMathTest` + 9 `WidgetMathTest`.

### Input: pena, mouse, keyboard
- M-Pencil diperlakukan sebagai mouse: tap = klik kiri, **tap 2× = klik kanan**, barrel button = klik kanan.
- Kursor pointer e-paper untuk pena/mouse, drag langsung untuk memindahkan ikon dan jendela.
- Keyboard internal Android tetap berfungsi dan bisa dipanggil kapan saja lewat tombol ⌨ di tray
  (atau `Ctrl+Alt+K`); layout menyesuaikan tinggi keyboard.
- Dukungan keyboard eksternal dengan 10+ pintasan gaya desktop.

### Aplikasi bawaan
- **Terminal** (`rb-sh`) — 28+ perintah: `apps/ls`, `open <nama>`, `neofetch`, `df`, `free`, `ps`,
  `style linux|macos|windows`, `palette`, `wall`, `icons`, `scale`, `tiling on|off`,
  `workspace 1..4` (`ws`), `wsmove 1..4`, `cal`, `wallpaper pattern|device|image`,
  `seconds on|off`, `arrange`, `safearea on|off|20`, `dock list`, `dock move <app> <slot>`,
  `floating on|off`, `widgets` / `widget width <dp>` / `widget size <name> <w> <h>` / `widget reset`,
  `invert`, `wmctrl`, `boot`, `help`, `exit`,
  dan peluncuran aplikasi langsung dengan mengetik namanya.
- **Applications** — app drawer dengan pencarian, long-press untuk menu, drag untuk pin.
- **Jendela aplikasi (app window)** — pembungkus desktop untuk aplikasi pihak ketiga: nama + ikon,
  geometry jendela, tombol *place here* / *open full screen* / *bring it back*, plus penjelasan
  status floating window di perangkat itu.
- **Settings** — gaya desktop, palet, **sumber & tone wallpaper**, **safe area kamera/sudut**,
  **keterbacaan ikon**, mode + bentuk ikon, skala UI, **input & devices
  (pena/mouse/keyboard)**, perilaku, tombol "Set as default launcher".
- **System monitor** — baterai, RAM, penyimpanan, jaringan, uptime, daftar jendela, grafik tren.
- **Notes** — catatan teks yang tersimpan otomatis.
- **Calendar** — bulan, agenda, task, birthday; semua bisa ditambah/edit/hapus dari UI.
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
| `Ctrl+Alt+1..4` | pindah virtual desktop |
| `Ctrl+Alt+Shift+1..4` | kirim jendela aktif ke desktop tersebut |
| `Super` (Meta) | Spotlight (cari aplikasi atau jalankan perintah shell) |
| `Esc` | tutup popup / spotlight / start menu |
| `Enter` | jalankan perintah di terminal & field satu baris |

### Boot sequence
Animasi BIOS singkat (bisa dimatikan di Settings) plus layar boot bergaya e-paper 1-bit.

## Instalasi di tablet

1. Unduh **APK release** (nama file berakhiran `-release.apk`) dari salah satu sumber:
   - halaman **Releases** → tag versi terbaru (mis. `v1.6.0`) atau tag `nightly` (selalu build terbaru), atau
   - tab **Actions** → run terakhir → artifact `retrobit-desktop-apk-*`.

   Di setiap rilis ada dua file: `-release.apk` (yang dipakai — sudah diminifikasi, di-sign,
   **tidak** debuggable, package `dev.retrobit.desktop`) dan `-debug-only-for-testing.apk`
   (build debug untuk pelacakan masalah, package `dev.retrobit.desktop.debug`, terpasang sebagai
   aplikasi terpisah dan **tidak** menimpa yang rilis). Ambil yang **release**.
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

1. **job `test`** — `./gradlew :app:testDebugUnitTest` (14 unit test untuk matematika tiling &
   edge-snapping); jumlah test/failure muncul di job summary dan sebagai check annotation
2. **job `build`** — checkout → JDK 17 → Android SDK (platform 35, build-tools 35.0.0) → Gradle (cache),
   lalu `./gradlew :app:assembleDebug :app:assembleRelease`
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
├─ ui/wm/                     # WindowManager, WindowFrame, DragState, LayoutMath (purified tiling/snap maths)
├─ ui/apps/                   # Terminal (rb-sh), Applications, Settings, System monitor, Notes, About
├─ ui/desktop/                # ShellLinux, ShellMacos, ShellWindows, widget, Spotlight, wallpaper
└─ ui/boot/BootScreen.kt      # animasi boot

app/src/test/java/dev/retrobit/desktop/ui/wm/LayoutMathTest.kt   # 14 unit test (CI: job "Unit tests")
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
