# Cara Build APK SMART META Lewat GitHub

Proyek ini sudah dilengkapi workflow GitHub Actions (`.github/workflows/build-apk.yml`)
yang otomatis build APK setiap kali kamu push kode. Tidak perlu install Android
Studio di komputer sendiri.

## 1. Buat repository di GitHub

1. Buka github.com → **New repository** → beri nama, misalnya `smart-meta-app`.
2. Jangan centang "Initialize with README" (biar tidak konflik dengan file yang sudah ada).

## 2. Push kode ke GitHub

Dari folder proyek hasil ekstrak zip:

```bash
git init
git add .
git commit -m "Initial commit: SMART META app"
git branch -M main
git remote add origin https://github.com/USERNAME/smart-meta-app.git
git push -u origin main
```

## 3. Tambahkan Secrets (kredensial Supabase)

Karena `.env` **tidak ikut ter-push** (memang sengaja di-`.gitignore`, supaya kredensial
tidak bocor ke publik), workflow butuh kredensial ini disimpan sebagai **GitHub Secrets**:

1. Di halaman repo GitHub → **Settings** → **Secrets and variables** → **Actions**.
2. Klik **New repository secret**, tambahkan dua secret ini satu per satu:
   - Name: `SUPABASE_URL` → Value: `https://xxxxx.supabase.co`
   - Name: `SUPABASE_ANON_KEY` → Value: anon key kamu

## 4. Jalankan build

Build otomatis jalan tiap kali kamu `git push` ke branch `main`. Bisa juga dipicu manual:

1. Buka tab **Actions** di repo GitHub.
2. Pilih workflow **Build APK** di sidebar kiri.
3. Klik **Run workflow** → **Run workflow**.

## 5. Download APK hasil build

1. Setelah build selesai (tanda centang hijau), klik build yang barusan jalan.
2. Scroll ke bagian **Artifacts** di bawah halaman.
3. Download **smart-meta-debug-apk** — isinya `app-debug.apk`, tinggal transfer ke HP Android dan install.

## Catatan penting

- **Ini APK debug**, cocok untuk testing. Untuk publish ke Play Store atau bagi-bagi
  APK "resmi" ke petugas/warga, perlu APK **release** yang ditandatangani dengan
  keystore sungguhan (bukan `debug.keystore` yang di-generate otomatis di workflow ini).
  Kalau sudah siap ke tahap itu, bilang saja — saya bantu siapkan workflow release +
  cara generate & simpan keystore-nya sebagai secret dengan aman.
- Proyek ini **belum menyertakan Gradle Wrapper** (`gradlew`). Workflow ini disiasati
  supaya tetap bisa build tanpa itu (pakai Gradle resmi dari GitHub Action). Tapi kalau
  kamu nanti buka project ini di Android Studio, Android Studio akan otomatis
  membuatkan `gradlew` — setelah itu commit & push filenya, supaya build lokal juga
  bisa jalan lewat `./gradlew assembleDebug` seperti proyek Android pada umumnya.
- Kalau build gagal, cek log di tab **Actions** — biasanya penyebabnya salah satu:
  secret belum diisi, versi Gradle tidak cocok dengan Android Gradle Plugin, atau
  error kompilasi Kotlin biasa (sama seperti kalau build di komputer sendiri).
