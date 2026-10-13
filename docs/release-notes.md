# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}

- **Widget bisa ditambah / dihapus dari homescreen**: hold (atau klik kanan) desktop kosong →
  `Add widget` / `Remove widget` / `Edit widgets`, plus panel `add or remove widgets` di mode edit,
  daftar di **Settings - display widgets**, dan terminal (`widget add notes`, `widget remove clock`,
  `widget layout`).
- **Semua widget bisa diubah ukurannya dengan hold**: tahan widget mana pun, seluruh badannya jadi
  handle resize; tepi kanan, tepi bawah, dan grip sudut tetap bisa ditarik langsung. Ada bingkai
  putus-putus, chip nama + ukuran hidup, dan tombol `^` `v` `fit` `remove` di tiap widget saat mode
  edit.
- **Ikon aplikasi benar-benar mengikuti palet sekarang**: bitmap ikon selalu digambar ulang untuk
  palet/mode/bentuk/accent baru (bug "ikon tidak berubah" diperbaiki), semua palet punya accent,
  dan Settings punya pratinjau ikon hidup + tombol `re-ink icons now`.
- **Stylus: klik kanan sekarang pakai HOLD** (bukan dobel-tap). Tahan pena = klik kanan, yaitu jalan
  masuk ke pengeditan (widget, ikon, menu desktop, menu jendela). Tap tetap klik kiri dan **tidak
  lagi tertunda 320 ms**; lama hold bisa dipilih 250/380/500/700 ms.

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
