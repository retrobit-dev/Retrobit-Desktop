# Retrobit Desktop {{TAG}}

**Yang dipasang: file yang namanya berakhiran `-release.apk`.**

| File di daftar bawah | Isinya | Dipasang? |
| --- | --- | --- |
| `retrobit-desktop-{{BUILD}}-release.apk` | build **rilis**: sudah diminifikasi, di-sign dengan keystore repo, **tidak** debuggable, package `dev.retrobit.desktop` | **Ya, pakai yang ini** |
| `retrobit-desktop-{{BUILD}}-debug-only-for-testing.apk` | build **debug** untuk pelacakan masalah (logcat, tanpa shrink); package `dev.retrobit.desktop.debug` | tidak perlu, hanya untuk testing |

## Yang baru di {{TAG}}


### Tema desktop kini memakai warna & chrome desktop yang **sebenarnya**
Sebelum ini tema bawaan desktop masih dicetak dengan mesin e-paper: warna desktopnya benar, tapi
jendelanya masih bergaya e-paper. Sekarang tema native menggambar **chrome desktop asli** — dan
karena dua mesin itu memang berbeda, hasilnya bukan lagi "e-paper yang diberi warna".

- **Windows 95**: muka jendela perak `#C0C0C0` dengan bingkai hitam, **title bar gradient navy
  `#000080` > biru `#1084D0`**, **tombol kotak ber-bevel** (klik lewat hold), **sudut persegi**,
  bayangan kotak keras satu lapis, desktop **teal `#008080`**, dan label ikon putih dengan plate
  seleksi **navy** seperti aslinya.
- **Windows 11**: permukaan Mica `#202020`, **title bar rata** `#2B2B2B`, tombol **glyph polos**
  dengan **dot aksen** `#60CDFF` di sudutnya, **sudut membulat 8 dp**, bayangan lembut tiga lapis,
  desktop `#1B1F24` > `#0F1216`, seleksi `#60CDFF`.
- **macOS Aqua / Aqua dark**: chrome salju `#F4F4F6` (dark `#1E1E20`), **title bar gradient**
  `#F7F7F8` > `#DCDCDE`, **traffic lights** merah `#FF5F57` / kuning `#FEBC2E` / hijau `#28C840`
  di kiri, **sudut membulat 10 dp**, seleksi biru sistem `#0A84FF`, desktop slate blue
  `#3F72A8` > `#1E4E7E` (dark: `#17191D` > `#0B0C0F`).
- **Ubuntu**: muka jendela terang `#F6F5F4`, **headerbar gelap** `#3D3D3D` > `#2C2C2C`, tombol
  GNOME rata dengan dot oranye, aksen `#E95420`, **sudut 12 dp**, desktop aubergine
  `#77216F` > `#4B1749` digambar **diagonal**.
- **Adwaita**: abu-abu libadwaita `#242424`, headerbar `#303030` > `#242424`, aksen `#78AEED`,
  desktop `#1A1A1A` > `#141414`, sudut 12 dp.
- **Yang hilang di tema native**: dither, scanline, registration mark, caption cetakan, dan
  bayangan cetak. Yang muncul: gradient sungguhan, bayangan lembut, sudut membulat sesuai tema,
  ikon berwarna penuh, dan **wallpaper berwarna** (posterisasi 16 level) di atas warna desktop.
- **Ikon & teks menyesuaikan desktop**: label ikon memakai warna yang dibaca di desktop tema itu,
  dan plate seleksi memakai **warna highlight desktop** (navy di Win95, biru sistem di Aqua,
  oranye di Ubuntu) — begitu juga tombol aktif, tag, dan baris menu yang dipilih.
- **Tombol jendela mengikuti tema**: traffic lights (macOS), kotak ber-bevel (Windows 95), atau
  glyph + dot aksen (Windows 11, Ubuntu, Adwaita).
- **Night mode melepas skin native.** `invert` / mode malam mengembalikan tampilan ke mesin
  e-paper, karena "Windows 95 gelap" tidak pernah ada. Palet e-paper tidak pernah terpengaruh.
- **6 tema e-paper tidak berubah sedikit pun** — Paper, Sepia, Tri-color, Ink, Phosphor, dan Amber
  tetap dicetak 1-bit seperti sebelumnya.
- Enam tema native tetap **satu ketukan = pindah desktop style + ikon berwarna** (masih bisa
  diganti lagi), tetap bisa dipanggil dari Settings, terminal (`palette win95`, `palette mac`,
  `palette gnome`, …), atau chip palet di panel.
- Unit test baru: setiap tema native harus punya skin dengan warna yang konsisten dengan paletnya,
  teks title bar terbaca di kedua ujung gradient, teks ikon terbaca di kedua ujung desktop,
  Windows 95 tetap persegi + gradient, hanya Aqua yang pakai traffic lights, hanya tema modern yang
  rata, dan `invert` wajib melepas skin.

### Tema ikon: setiap tema desktop sekarang membawa bentuk ikonnya sendiri
Tema palet sudah punya warna dan chrome desktop aslinya; sekarang **ikonnya ikut**: bukan cuma
berwarna, tapi juga berbentuk seperti desktop itu.

| Tema | Bentuk ikon |
| --- | --- |
| **Windows 95** | kotak **ber-bevel**: bingkai hitam tebal, kilau kertas di sisi atas-kiri, dan **pita aksen navy** di tepi bawah seperti tombol 95 |
| **Windows 11** | **kotak membulat** (radius 24%) tanpa garis luar, hanya hairline tipis dan kilau tipis di tepi atas |
| **Aqua / Aqua dark** | **tile membulat** (radius 26%) dengan edge pucat, **kilau kaca** melintang di bagian atas dan bayangan lembut di kaki tile |
| **Ubuntu** | kotak membulat dengan **trim oranye** `#E95420` di tepi bawah, khas Yaru |
| **Adwaita** | **lingkaran** dengan cincin tipis, khas GNOME |

- **Follow palette** (setelan bawaan) yang memilih: mengganti palet langsung mengganti bentuk ikon.
  Sebelum ini setelannya bernama "Follow desktop" dan hanya melihat gaya shell.
- **Palet e-paper tidak berubah**: tetap tile 1-bit bergaris tinta, bentuknya dari desktop style
  (Windows kotak, macOS membulat, Linux rata) — karena palet e-paper memang tidak punya desktop
  aslinya sendiri.
- **Warna tile ikut tema**: plat ikon memakai muka jendela tema, garis bingkainya memakai border
  tema (bukan warna teks), trimnya memakai aksen tema. Huruf penanda ikut membulat di bentuk baru.
- **Bisa dipin**: di *Settings - app icon shape* ada deretan chip yang menggambar **aplikasi kamu
  sendiri** dalam setiap bentuk (dengan warna palet aktif, dan penanda "this theme" pada bentuk yang
  paletnya pakai). Terminal: `icons shape aqua`, `icons shape auto`, `icons` untuk status,
  `icons accent on|off`, `icons letters on|off`.
- **Pratinjau hidup di Settings**: satu baris menampilkan aplikasi asli kamu dalam kesembilan bentuk,
  jadi perbedaan bevel 95 / squircle / kilau Aqua / trim Ubuntu / cincin GNOME terlihat sebelum
  dipilih.
- Unit test baru (`IconThemeTest`, 6 ekspektasi): setiap palet native memetakan ke bentuk desktopnya,
  palet e-paper tidak memin apa pun, "follow palette" lebih memilih palet daripada gaya shell,
  pilihan eksplisit selalu menang, dan daftar bentuk tetap rapi untuk baris chip.

### Sudut, menu, dan permukaan mengambang kini mengikuti tema juga
Tiga detail chrome yang tersisa disesuaikan supaya tema native benar-benar terasa seperti desktop
aslinya:

- **Jendela**: tema native yang bersudut membulat (Aqua 10 dp, Windows 11 8 dp, Ubuntu/Adwaita
  12 dp) sekarang membulat di **keempat sudut**, seperti jendela asli desktop itu. Windows 95 tetap
  kotak, dan palet e-paper tetap hanya sudut atas (gaya cetakan).
- **Menu konteks**: di tema native menunya membulat (maksimal 8 dp) dengan bingkai tipis warna
  border tema; di Windows 95 dan palet e-paper tetap kotak bergaris tinta.
- **Permukaan mengambang** memakai satu helper (`Modifier.shellPanel`): **start menu**, **Launchpad**,
  **panel Spotlight**, **dock macOS**, serta **title rail dan dock** yang dipakai aplikasi bawaan —
  semuanya mengambil radius tema, bingkai tipis tema, dan bayangan lembut; di palet e-paper tetap
  kartu kotak berbingkai tinta dengan bayangan checker keras.

### Widget desktop: sekarang ikut tema paletnya
Tema palet sudah membawa warna, chrome, dan bentuk ikon. Sekarang **widget** ikut juga: di tema
desktop native widget menggambar chrome desktop itu, bukan cetakan e-paper berwarna.

| Bagian widget | Palet e-paper (tetap) | Tema native (baru) |
| --- | --- | --- |
| Badan kartu | kertas + bingkai tinta 1.6 dp, kotak, bayangan kotak keras | muka jendela tema, **sudut membulat** tema (0–12 dp), border tema, **bayangan lembut** |
| Bar judul | strip **hatch** cetakan, judul tinta | **gradient judul asli tema** (Win95 navy `#000080` → biru `#1084D0`, Aqua `#F7F7F8` → `#DCDCDE`, Ubuntu `#3D3D3D` → `#2C2C2C`, Win11 rata, Adwaita gradient tipis) dengan teks berwarna header, tinggi bar sedikit lebih besar (21–23 dp) |
| Meter (baterai/RAM/disk) | sel tinta padat di atas kertas, setengah sel `mid` | **track membulat** di muka lembut tema, isian **aksen tema** (navy / biru sistem / oranye) |
| Kalender | hari terpilih = plat tinta | hari terpilih = **warna seleksi tema** (navy di Win95, biru sistem di Aqua, oranye di Ubuntu), sel membulat 4 dp |
| Tag baris agenda | kotak tinta + teks kertas | kotak **highlight tema** + teks highlight-nya |
| Mode edit | bingkai putus-putus **tinta**, kotak | bingkai putus-putus **aksen tema**, membulat sesuai sudut tema |
| Grip resize | garis tinta | garis **border tema** |

- **Ganti palet = ganti tampilan widget** tanpa mengubah ukuran, posisi, isi, atau pengaturannya.
  Semua widget lama (chrono, kalender, agenda, tasks, launcher, power, sysinfo, notes) ikut.
- **Palet e-paper tidak berubah sedikit pun** — semuanya masih tercetak: hatch, sel tinta, bayangan
  keras, bingkai tinta mode edit.
- Unit test baru (`WidgetSkinTest`, 6 ekspektasi): setiap palet native harus memetakan ke muka
  jendela/border/gradient/meter/plate miliknya, palet e-paper harus tetap tercetak, judul widget
  wajib terbaca di kedua ujung bar, meter wajib terlihat di atas track-nya, dan palet dengan bar
  rata (Windows 11) wajib mendapat bar rata sementara yang bergradasi tipis (Adwaita) tetap
  mendapat gradientnya.

### Jendela mengambang: aplikasi bawaan launcher tidak lagi langsung full screen
Aplikasi **bawaan launcher** sekarang terbuka sebagai **jendela kotak di desktop**, bukan memenuhi
layar begitu dibuka. Yang ikut pindah ke dalam jendela: **terminal, settings, notes, applications
(app drawer), system monitor, calendar, about**.

- **Geser** jendela dari **title bar**-nya; **resize dari 8 arah** — empat sisi + empat sudut, dengan
  pegangan yang bisa digenggam dari luar bingkai (ada penanda garis miring di sudut kanan bawah).
  Saat sedang di-resize, ukurannya tercetak hidup di title bar (`1024x768`).
- **Minimize**, **maximize**, dan **close** ada di title bar. Bentuk tombolnya mengikuti gaya desktop
  yang kamu pakai: tiga kotak kecil gaya GTK (Linux), lampu lalu lintas gaya macOS, atau gradient
  dither gaya Windows 95.
- **Maximize mengisi area desktop** — panel/taskbar dan dock tetap terlihat — dan menekan maximize
  lagi (atau double tap / double click di title bar) **mengembalikan jendela persis ke ukuran dan
  posisi sebelumnya**, bukan ke ukuran tebakan baru.
- **Ukuran & posisi terakhir diingat per aplikasi** (dan per halaman desktop), jadi terminal yang
  kamu atur lebar akan terbuka selebar itu lagi besok. Mau mulai dari bersih: tombol **reset window
  sizes** di *Settings - apps & windows* (terminal: `win reset`).
- **Snap ke tepi**: seret jendela ke tepi kiri/kanan untuk memenuhi setengah desktop, seret ke atas
  tengah untuk maximize. Saat masih diseret, tempat tujuannya digambar sebagai kotak bergaris putus.
- **Task list**: panel Linux punya menu **windows**, macOS menu **Window**, taskbar Windows 95 punya
  tombol per jendela (jendela yang di-minimize tercetak lebih pudar). Isinya: daftar jendela,
  `minimize all`, `close all`, `reset window sizes`. Di terminal: `win list`.
- **Minimize tidak menghilangkan jendela**: buka aplikasinya lagi dari ikon, dock, panel, atau taskbar
  dan jendela yang tadi turun muncul kembali pada ukuran yang sama. Tombol `back` juga menurunkannya.
- **Tiap halaman desktop punya jendelanya sendiri** (`win send 2` memindahkan jendela aktif ke
  halaman 2; `ctrl+alt+left/right` pindah halaman).
- **Pintasan keyboard**: `ctrl+alt+t` terminal, `ctrl+alt+w` tutup jendela aktif, `ctrl+alt+m`
  minimize semua, `ctrl+alt+up` maximize/restore, `ctrl+alt+tab` pindah jendela.

**Aplikasi pihak ketiga tidak berubah**: aplikasi terpasang tetap dibuka **penuh** dengan bingkai
cyberdeck digambar di sekelilingnya. Jendela di sini adalah jendela milik launcher untuk aplikasi
milik launcher sendiri — tidak ada permintaan *freeform window* ke Android, jadi tidak ada izin
sistem tambahan dan tidak ada aplikasi orang lain yang dikurung di dalam kotak.

### Keyboard: jendela tidak bergeser sedikit pun
Perilaku keyboard dirapikan jadi tiga mode, dan yang **default benar-benar diam**:

- **float over (default)** — keyboard mengapung di atas desktop; title rail, ikon, widget, panel,
  dock, **dan jendela mengambang** (bingkai, posisi, ukuran) tidak bergerak sedikit pun. Ini yang
  diminta: buka keyboard, tampilan tetap.
- **room inside apps** — desktop tetap diam juga, tapi bagian dalam aplikasi kita (terminal, notes,
  settings) menyediakan ruang supaya baris yang sedang diketik tidak tertutup keyboard.
- **slide up** — perilaku lama: seluruh desktop naik setinggi keyboard, untuk yang menyukainya.

Pilih di *Settings - apps & windows*, atau lewat terminal: `keyboard overlay|content|lift`
(`keyboard still` juga masih jalan dan berarti `overlay`).

### Pena kembali jadi pena di dalam kolom teks
Stylus tetap jadi kursor panah di desktop — tap = klik kiri, **hold = klik kanan**, drag = pindah
ikon — tapi begitu kamu masuk ke **mode input teks** (menyentuh kolom teks sampai kolom itu memegang
fokus), stylus **kembali ke fungsi aslinya sebagai pena**:

- **Panah e-paper hilang** dan diganti **I-beam** di atas kolomnya (penunjuk yang benar untuk teks).
- **Hold tidak lagi membuka menu klik kanan**, dan **drag tidak lagi memindahkan ikon/widget** di
  area kolom — jadi tidak ada lagi menu yang muncul mendadak saat kamu mau menyeleksi teks.
- **Seleksi teks, gagang seleksi, dan tulisan tangan stylus (handwriting Huawei)** masuk ke kolom apa
  adanya, karena launcher berhenti menerjemahkan gerakan pena jadi aksi mouse selama mode itu.
- Mouse tidak terpengaruh: tetap panah, klik kanan tetap klik kanan, wheel tetap menggulir.

Keluar dari mode itu gampang: **tap di desktop kosong**, tekan `Esc` / tombol `desktop` di rail,
pindah aplikasi — atau matikan fiturnya di *Settings - input & devices → M-Pencil becomes a pen inside
text fields* (terminal: `pen text off`).

Pengaturan pena lainnya sekarang punya UI dan perintah sendiri: **hold = klik kanan**
(`pen rightclick on|off`), **lama hold** 250 / 380 / 500 / 700 ms (`pen time 380`), dan
**panah e-paper** (`pen cursor on|off`); `pen` tanpa argumen mencetak statusnya.

### Tidak ada lagi floating window: satu aplikasi, satu layar (v1.13.0)
> Catatan: ini aturan untuk **aplikasi pihak ketiga**, dan tetap berlaku sampai sekarang. Sejak
> versi terbaru aplikasi **bawaan launcher** kembali punya jendela mengambang sendiri - lihat bagian
> paling atas.

Desktop-nya sekarang bersih - **ikon dan widget saja**. Membuka apa pun (aplikasi terpasang,
terminal, settings, notes) membuka **penuh**, dan bingkai cyberdeck (border hitam, title rail, dock)
digambar di sekelilingnya:

- **Aplikasi pihak ketiga**: dibuka biasa oleh Android dan memenuhi layar; chrome yang menggambar
  bingkainya. Launcher tidak pernah lagi meminta Android membuat *floating window* (freeform), jadi
  tidak ada lagi jendela yang melayang-layang di atas desktop atau task yang di-restart.
- **Aplikasi kita sendiri** (terminal, settings, applications, notes, system monitor, calendar,
  about): sekarang juga **penuh**, dengan bingkai, title rail (nama + jam hidup + tombol panel &
  desktop) dan **dock di bawah** yang sama bentuknya dengan dock chrome - jadi desktop terlihat sama
  dari sisi mana pun kamu melihatnya.
- Esc (atau tombol `desktop` di rail) keluar dari aplikasi kita; tombol Back juga.

### Yang dihapus (biar jujur)
- **Floating window / freeform**: `WindowedLaunch`, rectangle `setLaunchBounds`, refleksi
  `setLaunchWindowingMode`, pengaturan *float apps inside their window frame*, dan *apps follow their
  window*.
- **Kotak jendela di desktop**: pindah, resize 8 arah, minimize, maximize/restore, tiling, edge
  snapping, cascade, dan tombol "place here".
- Tombol daftar jendela di panel Linux, taskbar Windows, dan strip macOS - tidak ada jendela untuk
  didaftar. Terminal: `wmctrl`, `tiling`, `floating`, `appframe`, `wsmove` ikut hilang.
- Yang **tetap** ada: desktop per workspace (sekarang halaman ikon & widget), dock pintar
  pinned/most-used, quick panel, widget, icon grid, dan seluruh chrome cyberdeck.

### Perbaikan penting: quick panel benar-benar bekerja saat aplikasi lain di depan
- Sebelumnya tombol-tombol panel (brightness, rotate, night, mode dock, aturan app) dikirim lewat
  broadcast ke activity launcher, dan receiver-nya hanya terdaftar saat launcher **di depan** -
  persis kebalikan dari keadaan saat panel dipakai (launcher sedang di belakang, app lain yang
  memegang layar). Akibatnya tap di panel bisa tidak melakukan apa-apa.
- Sekarang panel memanggil launcher **langsung**: panel dan launcher berada di proses yang sama
  (`PanelChrome.onAction` → `vm.onPanelAction`), jadi tidak ada lagi broadcast yang bisa "mati".
  Kalau launcher memang sudah tidak ada, panel menutup diri daripada pura-pura berhasil.
- Kelas `PanelSwitch` (bus broadcast) dan receiver di `MainActivity` dihapus - satu jalur saja,
  dan jalur itu hidup selama chrome hidup.

### Brightness sekarang benar-benar mengubah layar, walau aplikasi lain di depan
- Dulu brightness hanya dipasang di **window launcher**, jadi saat kamu menggeser groove di panel
  sementara sebuah aplikasi memegang layar, tidak ada yang berubah terlihat (window launcher sedang
  tidak ada di layar).
- Sekarang nilai itu ikut dibawa oleh **window overlay yang kita punya** di situasi itu: title rail,
  dock, dan bingkai (`CyberdeckChrome.State.brightness` → `ChromeService.applyBrightness()`), dan
  panelnya sendiri membawa nilai itu selagi terbuka (`PanelService.refresh()`), jadi menggeser groove
  langsung terlihat gelap/terangnya.
- `auto` mengembalikan kontrol ke tablet (nilai `-1` = device), sama seperti sebelumnya; dan saat
  desktop yang di depan, window launcher tetap yang mengatur seperti dulu.

### Hemat baterai: chrome berhenti saat layar mati
- Service chrome memantau layar: saat **layar mati** tick per detik dihentikan dan rail diturunkan ke
  handle (bingkainya tetap, karena bingkai tidak digambar ulang); saat layar hidup lagi tick jalan
  kembali, rail muncul, dan hitungan auto-hide mulai dari nol. Tidak ada lagi jam yang berdetak di
  balik layar gelap.
- Rail yang sedang auto-hidden (tinggal grip) **tidak lagi digambar ulang tiap detik** - sebelumnya
  dua strip selebar layar digambar 60 kali per menit walaupun isinya tidak berubah sedikit pun.
- Receiver-nya didaftarkan lewat `ContextCompat` dengan `RECEIVER_NOT_EXPORTED` (targetSdk 34), jadi
  tidak bisa dijangkau aplikasi lain.

### Ukuran rail & dock bisa diatur: compact / normal / large / huge
- Baris baru **rail & dock size** di *Settings → cyberdeck chrome*, empat tombol `compact` /
  `normal` / `large` / `huge` di quick panel (bagian RAILS), dan terminal
  `cyberdeck size compact|normal|large|huge`.
- Ukurannya adalah **skala chrome sendiri** (0.85x / 1x / 1.2x / 1.4x), terpisah dari `scale` desktop:
  tinggi title rail (40 dp), tinggi dock (54 dp), ukuran huruf, glyph, ikon aplikasi dan area
  sentuh tombol semuanya ikut membesar atau mengecil - enak dibaca dari jauh kalau `large`, dan
  menyisakan lebih banyak layar kalau `compact`.
- Berlaku langsung tanpa menunggu: begitu ukurannya berubah, service mengukur ulang tinggi jendela
  rail saat itu juga (dulu baru pada transisi auto-hide berikutnya).
- Panelnya sendiri tetap aman di skala UI besar: isinya tumpukan baris tetap, jadi panel berhenti
  di 1.3x dan kartunya naik ke 700 dp - tidak ada bagian yang tertimpa footer.

### Quick panel: semua pengaturan cyberdeck tanpa keluar dari aplikasi
Tombol **settings di rail** (terminal `panel`, atau *quick panel now* di Settings) membuka panel di
atas aplikasi yang sedang jalan - tidak perlu pulang ke desktop:

- **Brightness** - geser groove-nya, atau `B-` / `B+` / `auto`. Nilai diingat, dan `auto`
  mengembalikan kontrol ke tablet. Panel membaca brightness window yang sebenarnya, jadi `B-`/`B+`
  mulai dari angka yang benar (bukan dari perkiraan).
- **Display** - `auto rotate` / `rotate locked`, `night on|off` (tukar ke palet gelap **Ink**, dan
  kembali ke palet hari yang kamu pakai), `chrome on|off`, `hide rails now`.
- **Rails** - dock menampilkan **pinned apps** atau **most used first**, title rail & dock on/off,
  dan auto-hide (on/off + 4s/6s/10s/20s).
- **In frame now** - aplikasi yang sedang terbuka: tap = bawa ke depan **dan** masukkan lagi ke kotak
  jendelanya, tahan = buka shell-nya di terminal.
- **No chrome for** - daftar aplikasi tanpa bingkai: tap chip = kembalikan bingkainya.

### Dock pintar: pinned atau most used
- Pembatas di dock sekarang **tombol**: tap untuk berpindah antara **aplikasi yang dipin** dan
  **aplikasi yang paling sering dipakai**. Berlaku untuk semua permukaan: dock desktop, sidebar
  Linux, taskbar Windows, dock macOS, widget launcher, dan dock cyberdeck.
- Di mode *most used first*, dua app pertama yang dipin tetap di tempatnya; sisanya diisi aplikasi
  yang benar-benar sering dibuka.
- **Tahan app di dock cyberdeck** untuk pin / unpin (chip yang dipin diberi tanda kotak kecil), dan
  setiap aksi mengeluarkan konfirmasi singkat di rail (`pinned to the dock: Files`).
- Terminal: `rail mode pinned|used` dan `pins` (daftar dock + jumlah pembukaan).

### Per-app rules, night mode, rotation lock
- **No chrome for** - aplikasi tertentu (kamera, game, player) bisa dikecualikan dari bingkai, dari
  Settings, dari panel, atau dari chip di rail. Kalau app itu muncul di depan, rail tetap muncul
  **4 detik** dengan alasan + cara mengembalikannya, jadi tidak pernah jadi jalan buntu.
- **Night mode** - satu tap (atau `night on`) menukar ke palet gelap Ink dan kembali lagi.
- **Rotation lock** - mengunci launcher pada orientasi sekarang, atau kembali ke auto.

### Status hidup: jam, baterai, sinyal, keyboard
- Rail menampilkan **jam `HH:mm:ss`** dan detiknya berganti tepat di batas detik (tick rail
  diselaraskan ke jam dinding, bukan "sekitar satu detik").
- **Baterai dan sinyal digambar, bukan ditulis**: gauge baterai kecil (isi proporsional + petir saat
  mengisi) dan empat bar sinyal yang penuh sesuai transport (wifi/ethernet 4, seluler/VPN 3), dengan
  label `WIFI`/`CELL`/`OFFLINE` di sebelahnya.
- **Tombol keyboard** di rail dan di dock: memunculkan / menyembunyikan keyboard layar untuk
  aplikasi yang sedang di depan (rail tidak pernah merebut fokus sendiri, jadi keyboardnya tetap
  milik app itu).
- **Jam di panel**: header quick panel ikut menampilkan jam yang sama, jadi kamu tahu waktu tanpa
  menutup app.
- **Detik yang benar-benar sejajar**: widget *chrono* di desktop dan jam rail sekarang memakai tick
  yang diselaraskan ke batas detik jam dinding, jadi angka detiknya berganti tepat waktu - bukan
  "kira-kira satu detik" yang perlahan melenceng.
- **Rail tetap terbaca di layar sempit**: kalau kunci + readout sudah memakan hampir seluruh rail
  (portrait, atau skala UI besar), rail menahan diri: label jaringan dilepas lebih dulu, lalu gauge
  baterai, sehingga judul aplikasi tidak pernah tertimpa.

### UX pass
- **Bingkai hitam bisa ditekan**: tap pada rail kosong = sembunyikan rail (atau munculkan lagi kalau
  sedang tersembunyi). Handle auto-hide juga bisa disentuh untuk memunculkannya.
- **Area sentuh tombol diperbesar** 3-4 dp dari kotak yang digambar, dan jari yang menggeser keluar
  dari tombol membatalkan tekanannya - rail tidak pernah salah pencet.
- **Tombol `eye`** di rail & dock: sembunyikan rail **hanya untuk app yang sedang di depan**; app
  berikutnya memunculkannya lagi.
- **Ikon baru** (pixel art 16x16 seperti yang lain): `EYE`, `SPARK` (brightness), `STAR` (most used),
  `ROTATE`, `BARS` (sinyal).
- Teks di-snap ke kelipatan 16 px dan glyph ke 8 px, jadi rail dan panel tetap tajam di semua skala.

### Perintah terminal baru
`panel`, `rail mode pinned|used`, `pins`, `night on|off`, `cyberdeck autohide on|off|<3..60>`.

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
