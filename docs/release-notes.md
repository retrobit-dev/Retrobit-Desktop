# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}

- **Desktop bersih**: label bantuan di homescreen dihapus — `no widgets`, `desktop is empty`,
  `marked * are on the desktop`, `hold a widget to edit or resize(s)`, dan `empty — tap to write`
  tidak muncul lagi. Widget kosong cukup tampil sebagai kartu kosong, dan mode edit ditandai bingkai
  putus-putus.
- **Detik jam sejajar dengan menit**: widget `chrono` sekarang menggambar `13:37:09` dalam satu blok
  angka dengan baseline yang sama (sebelumnya detik menggantung kecil di samping).
- **Widget kalender jauh lebih ringkas**: tanpa kotak di sekeliling tiap tanggal, header dengan
  `<` `>` `+` dalam satu baris, satu baris ringkasan hari terpilih (`wed, 14 oct 2026 - 3 entries`),
  maksimal tiga entri, dan tidak ada lagi editor di dalam widget (ketuk entri untuk membuka app
  Calendar). Tinggi defaultnya sekarang setinggi isi, jadi kartunya sekitar 300 dp, bukan 420 dp.
- **Empat widget baru**: `agenda` (14 hari ke depan), `tasks` (task belum selesai, sekali tekan
  selesai), `launcher` (pintasan aplikasi dock), dan `power` (baterai besar + status charge).
  Total sekarang delapan widget dan kolom menampung maksimal delapan.
- Perapian: `sysinfo` mengikuti ukuran manual, tombol widget di panel mode edit jadi delapan.

### Fitur dari v1.7.x (kalau kamu melompat dari v1.6.x)

- **Widget bisa ditambah / dihapus dari homescreen** (menu desktop, panel `widgets` di mode edit,
  Settings, terminal `widget add|remove|layout`).
- **Semua widget bisa diubah ukurannya dengan hold**, plus tepi kanan/bawah dan grip sudut; hold di
  tengah widget meresize ke arah terdekat.
- **Ikon aplikasi benar-benar mengikuti palet** (bug "ikon tidak berubah" diperbaiki: render key
  selalu digambar ulang, semua palet punya accent, ada pratinjau ikon hidup + `re-ink icons now`).
- **Stylus: klik kanan = HOLD** (bukan dobel-tap) dan itu jalan masuk pengeditan; tap tetap klik kiri
  tanpa jeda 320 ms, lama hold bisa dipilih 250/380/500/700 ms.
- Mode edit bisa ditinggalkan dengan tap di desktop, `Esc`, atau `done`, dan hold-to-edit bisa
  dimatikan (`widget hold on|off`).

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
