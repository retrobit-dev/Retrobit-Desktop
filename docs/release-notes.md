# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}

- **Hold = menu, tap = pilih, dobel klik = buka.** Di desktop, app drawer, dan launchpad: satu tap
  hanya **memilih** ikon (ikon dicetak terbalik + namanya tercetak), **dobel klik / dobel tap**
  membuka aplikasi, dan **hold** membuka menu aplikasi — persis kebiasaan desktop. `Enter` juga
  membuka ikon yang sedang terpilih, dan tap di area kosong melepas pilihan.
- **App drawer tidak lagi langsung membuka app**: menggulir daftar tidak pernah dihitung sebagai
  klik (gestur yang melewati touch slop tidak memicu aksi apa pun), jadi daftar bisa digulir dengan
  tenang walaupun jarinya berhenti di atas ikon.
- **Menambah app atau widget ke desktop**: panel baru **`add to desktop`** (menu desktop →
  `Add apps or widgets to the desktop…`, atau dari menu aplikasi) berisi daftar aplikasi yang bisa
  dicari — tap sekali memasang ke desktop, tap lagi melepas — plus delapan tombol widget, tombol
  `arrange`, dan penghitung `n apps on the desktop`. Menu aplikasi juga punya `Add to desktop` /
  `Add to dock`, dan terminal: `app add <nama>`, `app remove <nama>`.
- **Bisa dikembalikan ke satu klik**: *Settings - input & devices → Apps open with a double click /
  double tap (tap selects)*, atau terminal `doubleclick on|off`. Dock, sidebar, taskbar, start menu,
  dan widget `launcher` tetap satu klik seperti biasa.
- Aplikasi yang ditambahkan ke desktop langsung menempati sel kosong pertama, jadi tidak menimpa
  atau menumpuk ikon yang sudah ada.

### Fitur dari v1.8.0 (kalau kamu melompat dari v1.7.x)

- Desktop bersih tanpa teks bantuan, detik jam sejajar dengan menit (`13:37:09`), widget kalender
  jauh lebih ringkas (grid tanpa kotak, 3 entri, tanpa editor di dalam widget).
- Empat widget baru: `agenda`, `tasks`, `launcher`, `power` (total delapan).
- Widget bisa ditambah / dihapus dari homescreen dan diubah ukurannya dengan hold.
- Ikon aplikasi mengikuti palet (bug "ikon tidak berubah" diperbaiki) + pratinjau ikon hidup.
- Stylus: klik kanan = hold; tap tidak lagi tertunda.

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
