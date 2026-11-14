# Kenali Tanaman

Aplikasi Android untuk mengenali tanaman dari foto. Ambil foto (atau pilih dari galeri), lalu
aplikasi menampilkan nama tanaman, nama ilmiah, cara merawat, toksisitas, kondisi tanaman di
foto, masalah umum, manfaat, dan fakta menarik. Hasil tersimpan di riwayat.

## Struktur

- `core/` — modul Kotlin murni: memanggil Claude (SDK resmi `anthropic-java`) dengan foto dan
  JSON Schema, lalu mengurai jawabannya. Punya unit test, tidak butuh Android SDK.
- `app/` — aplikasi Android (Jetpack Compose, Material 3): kamera, galeri, riwayat, pengaturan.

## Menjalankan

1. Buka folder ini di Android Studio (versi terbaru), tunggu Gradle sync selesai.
2. Jalankan ke HP/emulator Android 9 (API 28) ke atas.
3. Di aplikasi, buka Pengaturan (ikon gerigi) dan tempel API key dari console.anthropic.com.

Dari terminal (butuh Android SDK):

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :core:test
```

## Catatan

- API key disimpan di perangkat dan dikirim langsung ke API Anthropic. Ini cocok untuk
  pemakaian pribadi. Untuk rilis ke Play Store, pindahkan pemanggilan AI ke server Anda sendiri
  supaya key tidak ada di aplikasi.
- Model diatur di `PlantIdentifier.DEFAULT_MODEL`.
