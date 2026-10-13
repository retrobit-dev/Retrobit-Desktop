# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}


### Cyberdeck: kotak jendela sekarang tetap terlihat di atas aplikasi

**Masalahnya:** Android hanya mengizinkan aplikasi biasa menaruh aplikasi lain di dalam kotaknya lewat
**freeform window** (jendela sistem yang bisa digeser). Banyak tablet - termasuk yang memakai
EMUI/HarmonyOS - tidak punya mode jendela itu: walau opsi pengembang *force activities to be
resizable* sudah dinyalakan, rect yang kita kirim diabaikan dan aplikasi membuka **full screen**,
sehingga kotak jendela yang ada di belakangnya tidak pernah terlihat.

**Jalan keluarnya sekarang ada dua, dipakai bersama-sama:**

1. **Meminta jendela freeform secara eksplisit.** Selain rect (`setLaunchBounds`), launcher sekarang
   juga memanggil `setLaunchWindowingMode(FREEFORM)` (lewat reflection; kalau build-nya menolak, tidak
   apa-apa). Pada tablet yang mendukung freeform, aplikasi benar-benar mengapung di dalam kotaknya.
2. **Kalau tablet memang tidak bisa: cyberdeck chrome.** Launcher *menggambar sendiri* kotak itu di
   atas aplikasi yang full screen — bingkai 3dp, **title rail** (nama aplikasi, jam, baterai, tombol
   `fit frame` / `desktop` / `x`), dan **dock** di bawah (tombol desktop, app yang dipin, jendela yang
   sedang terbuka, dan tombol sembunyikan). Jadi setiap aplikasi tetap terlihat seperti jendela dari
   desktop ini, apa pun yang dilakukan sistem.

Cara memakainya:
- *Settings - cyberdeck chrome* → **Draw the desktop frame over full-screen apps** (butuh izin
  "display over other apps" - ada tombol **grant permission**), plus **preview 8s** untuk melihat
  hasilnya tanpa keluar dari desktop.
- Terminal: `cyberdeck on` / `cyberdeck off` / `cyberdeck preview` / `cyberdeck grant`, dan
  `cyberdeck` tanpa argumen untuk status.
- Saat chrome aktif, launcher berjalan sebagai foreground service kecil (dengan notifikasi
  importance MIN yang bisa ditutup lewat tombol `x` di rail) supaya bingkai tidak hilang saat
  launcher di belakang. Tengah layar **tidak** ditutup overlay apa pun, jadi aplikasi tetap menerima
  semua gesturnya, dan dock sengaja diberi jarak dari gesture bar supaya "swipe naik = home" tetap
  bisa.
- Chrome hanya muncul saat launcher benar-benar tidak terlihat (`onStop`). Kalau aplikasi berhasil
  mengapung (hanya `onPause`), chrome tidak ikut muncul - tidak ada bingkai dobel.

### Yang lain di {{TAG}}
- Jendela aplikasi sekarang jujur soal apa yang terjadi: kalau sistem membuka aplikasinya full screen
  padahal rect sudah dikirim, jendelanya menulis **"the system opened this app full screen - this
  tablet has no floating windows"** dan mengarahkan ke cyberdeck chrome.
- Baris status di *Settings - apps & windows* menyebutkan apakah build ini menjawab permintaan
  freeform window.
### Cyberdeck chrome: lebih mudah dibaca, bingkai hitam, rail bisa sembunyi
- **Tulisan dan ikon di rail & dock diperbesar** dan tidak lagi buram: ukuran teks di-snap ke
  kelipatan 16 px (grid em font Pixel Operator) dan glyph/ikon ke kelipatan 8 px, jadi huruf dan
  ikon tergambar tajam berapa pun skala UI-nya. Judul aplikasi di title rail sekarang memakai
  Pixel Operator **bold**, tinggi rail 40 dp, dock 54 dp.
- **Dock sekarang menampilkan ikon aplikasi aslinya** (dicetak grayscale, di dalam kotak paper
  berbingkai hitam) dengan namanya di bawah - bukan cuma teks. Ikon di-cache per paket.
- **Bingkai hitam polos.** Sebelumnya bingkai memakai warna ink palet (di palet Ink jadi terang);
  sekarang selalu **hitam**, jadi tepi jendela selalu terbaca di atas aplikasi apa pun.
- **Judul rail menghindari lubang kamera**: kalau ada cutout di kiri, judul dimulai setelah cutout.
- **Rail auto-hide**: setelah 4/6/10/20 detik (pilihan di Settings, default 6 detik) title rail dan
  dock menyusut jadi **handle tipis** di tepi atas/bawah - hanya bingkai hitam yang selalu ada.
  Sentuh handle-nya untuk memunculkan kembali; rail juga otomatis muncul sebentar setiap kali
  aplikasi di depan berganti. Bisa dimatikan: *Settings - cyberdeck chrome → hide the rails*, atau
  terminal `cyberdeck autohide on|off|6`.
- Status di title rail sekarang menampilkan **jam + baterai + jaringan**.


### Terminal: tombol cepat jadi ikon
- Empat tombol cepat di bawah terminal (`help`, `previous`, `next`, `clear`) sekarang **hanya ikon**,
  bukan kata: tanda tanya, panah atas, panah bawah, dan tanda silang. Tidak ada lagi label teks yang
  memakan lebar baris perintah.
- Glyph-nya digambar sendiri sebagai pixel-art 16x16 di `ui/components/Glyphs.kt` (empat glyph baru:
  `HELP`, `UP`, `DOWN`, `CLOSE`), jadi tetap tajam di semua skala UI dan warnanya ikut palet.

### Rapikan supaya benar-benar siap dipakai
- **Ikon tidak lagi dibuang setiap kembali ke homescreen.** Sebelumnya setiap kali launcher muncul
  lagi (dan setiap ada aplikasi baru terpasang) seluruh cache ikon dihapus, jadi semua ikon digambar
  ulang — boros dan kadang terlihat berkedip. Sekarang cache hanya dibuang kalau daftar aplikasi
  benar-benar berubah, dan ukurannya dibatasi **berdasarkan memori** (12 MB), bukan jumlah entri.
- **Timer jam berhenti saat launcher tidak di depan.** Dulu loop 1 detik terus berjalan di latar
  belakang sambil menanyakan baterai, memori, disk, dan jaringan ke sistem setiap detik. Sekarang
  loop-nya hanya hidup saat launcher benar-benar tampil, dan angka sistem dibaca tiap 5 detik.
- **Format tanggal tidak lagi dibuat ulang tiap detik** di widget jam, panel, menu bar, dan taskbar.
- **Waktu kalender disimpan dengan angka ASCII** (`Locale.US`), jadi entri tetap terbaca di
  perangkat dengan bahasa yang memakai angka non-Latin.
- **Angka sistem (memori, disk) memakai `Locale.US`** supaya tidak muncul `3,1G` di perangkat
  berkoma-desimal.
- **Satu string UI memakai panah `→` yang tidak ada di font Pixel Operator** — diganti `>`.
- **UI scale dibatasi 0.6-2.0** saat dibaca, jadi nilai lama yang rusak tidak bisa membuat desktop
  tidak terbaca.
- **Launcher tidak akan crash kalau layanan sistem tidak tersedia**: `LauncherApps` /
  `UserManager` sekarang aman bila `null`.
- **Layar boot menampilkan angka sebenarnya**: jumlah core CPU, ABI, dan resolusi layar perangkat.

- **Semua aplikasi sekarang selalu terbungkus kotak jendela.** Membuka aplikasi dari mana saja
  (desktop, app drawer, dock, sidebar, taskbar, Spotlight) membuka **jendela desktop**-nya dulu —
  persis seperti jendela Settings / Terminal: title bar, minimize, maximize, close, geser, resize 8
  arah, snap ke tepi, pindah workspace, muncul di taskbar. Tidak ada lagi aplikasi yang muncul
  begitu saja tanpa bingkai.
- **Maximize = kotak jendela sebesar desktop, dan bisa di-restore ke ukuran semula.** Tekan maximize
  (atau dobel tap title bar, `Ctrl+Alt+↑`, geser ke tengah-atas) → bingkai memenuhi seluruh area
  desktop dan aplikasi di dalamnya ikut memenuhi kotak itu. Tekan lagi → jendela kembali **persis**
  ke ukuran dan posisi terakhir sebelum maximize. Matematikanya dipisah ke `ui/wm/MaximizeMath.kt`
  dengan 9 unit test, jadi maximize → restore → maximize tidak pernah membuat jendela mengecil
  sendiri atau melompat ke tengah layar.
  Catatan: panel dan dock launcher tetap terlihat (maximize memenuhi area desktop, bukan menutupi
  panel), seperti maximize di Linux/macOS/Windows.
- **App drawer bukan lagi layar penuh.** *Applications* sekarang jendela biasa berisi **daftar yang
  bisa digulir** — satu baris per aplikasi: ikon, nama, nama paket, dan berapa kali dibuka (`x12`).
- **Urutan app drawer: paling sering dipakai dulu, baru A–Z.** Setiap pembukaan aplikasi dicatat,
  lalu daftar dibagi dua bagian dengan pemisah **most used** dan **all apps, a-z**. Aplikasi yang
  kamu pakai sehari-hari selalu di atas, tanpa perlu mengatur apa pun. Terminal: `usage` (10
  teratas) / `usage clear` (lupakan hitungan, kembali A–Z).
- **Panel launchpad gaya macOS juga tidak lagi menutupi seluruh layar**: sekarang panel ± 62% × 76%
  layar dengan kolom pencarian dan daftar yang sama; tap di desktop yang digelapkan untuk menutup.
- Urutan yang sama dipakai Spotlight (6 aplikasi teratas) dan daftar aplikasi di start menu Windows.
- *Settings - apps & windows* diperbarui: **Float apps inside their window frame** (on = aplikasi
  diminta mengapung di dalam kotak jendelanya; off = aplikasi membuka full screen sementara kotak
  jendelanya tetap jadi entri taskbar), plus penjelasan maksud maximize/restore.

### Fitur dari v1.9.x (kalau kamu melompat dari v1.8.x)

- **"Place here" sudah benar**: rect yang dikirim ke sistem adalah daerah isi jendela (di bawah title
  bar, di dalam border) + safe area, dalam piksel layar; aplikasi juga mengikuti jendelanya saat
  digeser/di-resize (bisa dimatikan), dan tombol `restart into frame` memindahkan aplikasi yang
  sudah berjalan.
- Tap = pilih, dobel klik/tap = buka, hold = menu; app drawer bisa digulir tanpa membuka aplikasi.
- Panel `add to desktop` untuk menambah app maupun widget ke homescreen.
- Desktop bersih tanpa teks bantuan, detik jam sejajar dengan menit, kalender ringkas, delapan widget.

## Cara pasang

1. Unduh **`-release.apk`** (yang ukurannya lebih kecil) di tablet.
2. Buka file itu, izinkan *Install unknown apps* untuk aplikasi pengelola file.
3. Tekan tombol **Home** → pilih **Retrobit Desktop** → **Always**.
4. Opsional: buka **Settings → Launcher → Set as default launcher**.

APK debug package-nya beda (`dev.retrobit.desktop.debug`), jadi kalaupun dipasang ia akan muncul sebagai
aplikasi kedua dan **tidak** menimpa yang rilis.

## Catatan

- Semua build dari repo ini memakai keystore yang sama, jadi versi baru bisa dipasang **langsung di atas**
  versi lama tanpa uninstall.
- Launcher ini murni lokal: tanpa akun, tanpa jaringan.
- Kalau Huawei memblokir sideload, matikan **Pure mode** di *Settings → System & updates* lalu instal ulang.
