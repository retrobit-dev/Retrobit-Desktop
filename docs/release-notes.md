# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}

- **"Place here" benar-benar bekerja sekarang.** Tiga sebab kenapa dulu tidak jalan:
  1. rect yang dikirim adalah **seluruh bingkai jendela**, bukan daerah isinya — jadi aplikasi
     menutupi title bar dan terlihat "tidak berada di dalam kotak";
  2. rect dikirim dalam **koordinat desktop**, tanpa **safe area** (kamera + sudut melengkung) dan
     tanpa konversi ke **piksel layar**, padahal `ActivityOptions.setLaunchBounds` meminta piksel
     layar;
  3. ditambah lagi, rect hanya dikirim kalau perangkat melaporkan fitur freeform; di tablet yang
     bisa mengapung lewat PiP/PC mode, permintaannya tidak pernah dikirim sama sekali.
- Sekarang rect = **daerah isi jendela di bawah title bar, di dalam border, ditambah safe area,
  dalam piksel layar**, dan selalu dikirim. Matematikanya ada di `ui/wm/AppWindowMath.kt`
  (**9 unit test**: title bar, border, safe area, jendela keluar layar, jendela lebih besar dari
  layar, bingkai terlalu kecil, skala UI).
- **Tiga tombol baru di jendela aplikasi**: `place here` (letakkan di rect sekarang),
  `restart into frame` (jalankan ulang aplikasi sehingga pasti mendarat di rect baru — satu-satunya
  cara publik karena Android tidak mengizinkan launcher memindahkan task aplikasi lain), dan
  `bring it back`. Jendela juga menampilkan **rect frame**, **rect yang dikirim ke sistem**, safe
  area, skala UI, dan density, jadi kalau ada selisih kita bisa lihat angkanya.
- **Aplikasi mengikuti jendelanya**: geser title bar atau resize tepi → setelah 420 ms rect dikirim
  ulang otomatis. Matikan lewat *Settings - apps & windows → Apps follow their window* atau terminal
  `appframe follow on|off`; paksa sekali lewat terminal `appframe place <app>`.

### Fitur dari v1.9.x (kalau kamu melompat dari v1.8.x)

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
