# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}

- **Keluar dari mode edit widget lebih gampang**: tap di desktop kosong, tekan `Esc`, tombol `done`
  di panel, atau `Done editing widgets` di menu desktop.
- **Tombol penutup untuk hold-to-edit**: kalau jari yang beristirahat di widget sering membuat
  homescreen masuk mode edit tanpa sengaja, matikan di *Settings - display widgets → Hold a widget
  on the home screen to edit & resize it*, atau lewat terminal `widget hold on|off`. Resize dengan
  menarik tepi kanan / tepi bawah / grip sudut tetap jalan, dan mode edit bisa dibuka dari menu
  desktop atau Settings.
- Perapian internal (import yang tidak terpakai dibuang, tidak ada perubahan perilaku lain).

### Fitur dari v1.7.0 (kalau kamu melompat dari v1.6.x)

- **Widget bisa ditambah / dihapus dari homescreen**: hold (atau klik kanan) desktop kosong →
  `Add widget` / `Remove widget` / `Edit widgets`, panel `add or remove widgets` di mode edit,
  daftar di **Settings - display widgets**, dan terminal (`widget add notes`, `widget remove clock`,
  `widget layout`).
- **Semua widget bisa diubah ukurannya dengan hold** (tahan di widget mana pun), atau lewat tepi dan
  grip sudut; ada bingkai putus-putus + chip nama/ukuran + tombol `^` `v` `fit` `remove`.
- **Ikon aplikasi benar-benar mengikuti palet** (bug "ikon tidak berubah" diperbaiki: render key
  selalu digambar ulang, semua palet punya accent, ada pratinjau ikon hidup + `re-ink icons now`).
- **Stylus: klik kanan = HOLD** (bukan dobel-tap), dan itu jalan masuk ke pengeditan; tap tetap klik
  kiri tanpa jeda 320 ms, lama hold bisa dipilih 250/380/500/700 ms.

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
