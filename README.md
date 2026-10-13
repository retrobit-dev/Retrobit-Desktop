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

### Aplikasi bawaan
- **Terminal** (`rb-sh`) — 25+ perintah: `apps/ls`, `open <nama>`, `neofetch`, `df`, `free`, `ps`,
  `style linux|macos|windows`, `palette`, `wall`, `icons`, `scale`, `tiling on|off`, `invert`,
  `wmctrl`, `boot`, `help`, `exit`, dan peluncuran aplikasi langsung dengan mengetik namanya.
- **Applications** — app drawer dengan pencarian, long-press untuk menu, drag untuk pin.
- **Settings** — gaya desktop, palet, wallpaper, mode ikon, skala UI, perilaku, tombol
  "Set as default launcher".
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
| Klik kanan mouse / tombol barrel M-Pencil | menu konteks pada desktop dan ikon |
| `Ctrl+Alt+T` / `Ctrl+Alt+S` / `Ctrl+Alt+M` / `Ctrl+Alt+W` / `Ctrl+Alt+D` | terminal / settings / minimize all / close window / menu desktop |
| Tombol Super / Meta | buka Spotlight (cari aplikasi atau jalankan perintah shell) |
| Tombol Back | tutup popup → minimize semua jendela |

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

Font disertakan dari Google Fonts (SIL Open Font License 1.1, lihat `licenses/`):
VT323 dan IBM Plex Mono. Sisanya buatan sendiri untuk proyek ini.
