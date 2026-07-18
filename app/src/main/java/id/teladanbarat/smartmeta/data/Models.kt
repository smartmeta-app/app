package id.teladanbarat.smartmeta.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    @SerialName("admin") ADMIN,
    @SerialName("petugas") PETUGAS,
    @SerialName("warga") WARGA
}

@Serializable
enum class PetugasJenis {
    @SerialName("melati") MELATI,
    @SerialName("bestari") BESTARI
}

@Serializable
enum class LaporanStatus {
    @SerialName("baru") BARU,
    @SerialName("diproses") DIPROSES,
    @SerialName("selesai") SELESAI,
    @SerialName("ditolak") DITOLAK
}

@Serializable
enum class ShiftStatus {
    @SerialName("masuk") MASUK,
    @SerialName("keluar") KELUAR
}

@Serializable
enum class PoinTxType {
    @SerialName("setor_sampah") SETOR_SAMPAH,
    @SerialName("tukar_sembako") TUKAR_SEMBAKO,
    @SerialName("bayar_pajak") BAYAR_PAJAK,
    @SerialName("transfer_masuk") TRANSFER_MASUK,
    @SerialName("transfer_keluar") TRANSFER_KELUAR
}

@Serializable
data class Zona(
    @SerialName("id") val id: String? = null,
    @SerialName("nama_zona") val namaZona: String,
    @SerialName("deskripsi") val deskripsi: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("radius_meter") val radiusMeter: Int = 150,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Profile(
    @SerialName("id") val id: String,
    @SerialName("role") val role: UserRole,
    @SerialName("nama") val nama: String,
    @SerialName("no_hp") val noHp: String? = null,
    @SerialName("alamat") val alamat: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    @SerialName("jenis_petugas") val jenisPetugas: PetugasJenis? = null,
    @SerialName("zona_id") val zonaId: String? = null,
    @SerialName("jam_kerja_mulai") val jamKerjaMulai: String? = null,
    @SerialName("jam_kerja_selesai") val jamKerjaSelesai: String? = null,
    @SerialName("poin_saldo") val poinSaldo: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class LokasiPetugas(
    @SerialName("petugas_id") val petugasId: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class Absensi(
    @SerialName("id") val id: String? = null,
    @SerialName("petugas_id") val petugasId: String,
    @SerialName("status") val status: ShiftStatus,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("foto_selfie_url") val fotoSelfieUrl: String,
    @SerialName("dalam_radius") val dalamRadius: Boolean,
    @SerialName("waktu") val waktu: String? = null
)

@Serializable
data class Laporan(
    @SerialName("id") val id: String? = null,
    @SerialName("pelapor_id") val pelaporId: String? = null,
    @SerialName("petugas_id") val petugasId: String? = null,
    @SerialName("jenis") val jenis: String,
    @SerialName("deskripsi") val deskripsi: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("status") val status: LaporanStatus = LaporanStatus.BARU,
    @SerialName("catatan_admin") val catatanAdmin: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class BankSampahJenis(
    @SerialName("id") val id: String? = null,
    @SerialName("nama_sampah") val namaSampah: String,
    @SerialName("poin_per_kg") val poinPerKg: Int,
    @SerialName("aktif") val aktif: Boolean = true
)

@Serializable
data class BankSampahTransaksi(
    @SerialName("id") val id: String? = null,
    @SerialName("warga_id") val wargaId: String,
    @SerialName("tipe") val tipe: PoinTxType,
    @SerialName("jumlah_poin") val jumlahPoin: Int,
    @SerialName("berat_kg") val beratKg: Double? = null,
    @SerialName("jenis_sampah_id") val jenisSampahId: String? = null,
    @SerialName("tujuan_warga_id") val tujuanWargaId: String? = null,
    @SerialName("keterangan") val keterangan: String? = null,
    @SerialName("diverifikasi_oleh") val diverifikasiOleh: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatPesan(
    @SerialName("id") val id: String? = null,
    @SerialName("pengirim_id") val pengirimId: String,
    @SerialName("penerima_id") val penerimaId: String,
    @SerialName("laporan_id") val laporanId: String? = null,
    @SerialName("pesan") val pesan: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    @SerialName("dibaca") val dibaca: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Notifikasi(
    @SerialName("id") val id: String? = null,
    @SerialName("judul") val judul: String,
    @SerialName("isi") val isi: String,
    @SerialName("target_role") val targetRole: UserRole? = null,
    @SerialName("target_zona_id") val targetZonaId: String? = null,
    @SerialName("dikirim_oleh") val dikirimOleh: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
