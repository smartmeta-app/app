package id.teladanbarat.smartmeta.ui.theme

import androidx.compose.ui.graphics.Color

// Palet ini sengaja disamakan dengan dashboard admin (web) supaya identitas
// visual SMART META konsisten di semua platform: latar teal gelap ala pusat
// kendali lapangan, aksen amber untuk status "live"/perhatian, hijau sage
// untuk kategori Melati, biru berdebu untuk kategori Bestari.

// --- Aksen inti (dipakai di kedua mode) ---
val SignalAmber = Color(0xFFE8A33D)   // primary — status live, aksi utama
val MelatiSage = Color(0xFF7FB88F)    // kategori Melati (kompos)
val BestariDusty = Color(0xFF6E9BC7)  // kategori Bestari (anorganik)
val DangerCoral = Color(0xFFD9695F)

// --- Mode Gelap ---
val TealDeep = Color(0xFF0F1E1B)      // background
val TealPanel = Color(0xFF16302A)     // surface / card
val TealLine = Color(0xFF274038)      // border/divider halus
val IvoryText = Color(0xFFEFEAE0)     // teks utama di atas latar gelap

// --- Mode Terang ---
val CreamBackground = Color(0xFFFAF8F3)
val CreamSurface = Color(0xFFFFFFFF)
val InkText = Color(0xFF1C2521)
val LineLight = Color(0xFFE4DFD3)

// Warna "on-X" aksen (teks/ikon di atas warna aksen, dipakai kedua mode)
val OnAmber = Color(0xFF241705)
val OnSage = Color(0xFF0B1F12)
val OnDusty = Color(0xFF071522)
