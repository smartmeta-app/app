package id.teladanbarat.smartmeta.data

import android.util.Log
import id.teladanbarat.smartmeta.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Lapisan akses data ke Supabase.
 *
 * TIDAK ADA mode simulasi/demo/data palsu di sini. Semua StateFlow publik
 * (zonas, profiles, laporan, dst) hanya pernah diisi dari hasil query
 * Supabase yang sesungguhnya — kalau kosong, artinya memang belum ada data
 * atau belum berhasil dimuat, BUKAN placeholder.
 *
 * Setelah login, panggil [startRealtimeSync] sekali supaya semua StateFlow
 * otomatis mengikuti perubahan data secara realtime (peta, chat, laporan,
 * notifikasi ter-update sendiri tanpa perlu refresh manual).
 */
object SupabaseService {
    private const val TAG = "SupabaseService"

    /** true kalau URL/key sudah diisi dengan benar. UI wajib menampilkan
     * peringatan "aplikasi belum dikonfigurasi" kalau ini false. */
    var isConfigured = false
        private set

    private lateinit var client: SupabaseClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentProfile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _currentProfile.asStateFlow()

    // Cache reaktif — HANYA diisi dari Supabase sungguhan lewat refresh*()
    // atau realtime sync. Tidak pernah diisi data bikinan/mock.
    private val _zonas = MutableStateFlow<List<Zona>>(emptyList())
    val zonas: StateFlow<List<Zona>> = _zonas.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _laporan = MutableStateFlow<List<Laporan>>(emptyList())
    val laporan: StateFlow<List<Laporan>> = _laporan.asStateFlow()

    private val _bankSampahJenis = MutableStateFlow<List<BankSampahJenis>>(emptyList())
    val bankSampahJenis: StateFlow<List<BankSampahJenis>> = _bankSampahJenis.asStateFlow()

    private val _transactions = MutableStateFlow<List<BankSampahTransaksi>>(emptyList())
    val transactions: StateFlow<List<BankSampahTransaksi>> = _transactions.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatPesan>>(emptyList())
    val chats: StateFlow<List<ChatPesan>> = _chats.asStateFlow()

    private val _notifikasi = MutableStateFlow<List<Notifikasi>>(emptyList())
    val notifikasi: StateFlow<List<Notifikasi>> = _notifikasi.asStateFlow()

    private val _lokasiPetugas = MutableStateFlow<List<LokasiPetugas>>(emptyList())
    val lokasiPetugas: StateFlow<List<LokasiPetugas>> = _lokasiPetugas.asStateFlow()

    private val _myAbsensiHariIni = MutableStateFlow<List<Absensi>>(emptyList())
    val myAbsensiHariIni: StateFlow<List<Absensi>> = _myAbsensiHariIni.asStateFlow()

    private var realtimeStarted = false

    // true setelah app selesai mengecek apakah ada sesi login tersimpan dari
    // sebelumnya. UI menunggu ini true dulu sebelum memutuskan tampilkan
    // LoginScreen atau langsung ke dashboard — supaya user tidak "kelihatan"
    // logout sekilas padahal sebenarnya cuma masih proses pengecekan.
    private val _sessionCheckDone = MutableStateFlow(false)
    val sessionCheckDone: StateFlow<Boolean> = _sessionCheckDone.asStateFlow()

    init {
        initializeClient()
    }

    /** Dipanggil sekali saat app baru dibuka (MainActivity). supabase-kt
     * secara default SUDAH menyimpan token sesi ke penyimpanan lokal HP
     * setiap kali login berhasil ("ingat akun" otomatis dari librarynya) —
     * masalahnya sebelumnya app tidak pernah CEK sesi tersimpan itu saat
     * dibuka lagi, jadi selalu diarahkan ke LoginScreen walau sebenarnya
     * masih punya sesi valid. Fungsi ini menutup celah itu. */
    suspend fun restoreSession() {
        if (!isConfigured) {
            _sessionCheckDone.value = true
            return
        }
        try {
            // Tunggu sampai supabase-kt selesai memuat sesi tersimpan dari
            // disk (kalau ada) — statusnya "Initializing" sesaat lalu
            // berubah jadi Authenticated/NotAuthenticated.
            val status = client.auth.sessionStatus
                .first { it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing }

            val userId = client.auth.currentUserOrNull()?.id
            if (status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated && userId != null) {
                val profile = fetchProfileFromSupabase(userId)
                _currentProfile.value = profile
                refreshAll()
                startRealtimeSync()
                Log.i(TAG, "Sesi login sebelumnya berhasil dipulihkan untuk ${profile.nama}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulihkan sesi tersimpan, user perlu login manual.", e)
        } finally {
            _sessionCheckDone.value = true
        }
    }

    private fun initializeClient() {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()

        val validUrl = url.isNotEmpty() && url != "YOUR_SUPABASE_URL" && url.startsWith("http")
        val validKey = key.isNotEmpty() && key != "YOUR_SUPABASE_ANON_KEY"

        if (!validUrl || !validKey) {
            Log.e(TAG, "SUPABASE_URL / SUPABASE_ANON_KEY belum diisi di .env. Aplikasi tidak akan bisa memuat data.")
            isConfigured = false
            return
        }

        client = createSupabaseClient(supabaseUrl = url, supabaseKey = key) {
            install(Postgrest)
            install(Auth) {
                // Eksplisit dinyalakan (walau ini default bawaan library) supaya
                // jelas dan tidak bergantung pada asumsi default versi tertentu:
                // sesi login disimpan ke penyimpanan lokal HP dan token
                // di-refresh otomatis sebelum kedaluwarsa, tanpa perlu login ulang.
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Realtime)
            install(Storage)
        }
        isConfigured = true
        Log.i(TAG, "Supabase client initialized.")
    }

    private fun requireClient(): SupabaseClient {
        check(isConfigured) { "Supabase belum dikonfigurasi (SUPABASE_URL/SUPABASE_ANON_KEY kosong)." }
        return client
    }

    // ---------------------------------------------------------------------
    // AUTH
    // ---------------------------------------------------------------------

    /** Login. Melempar exception kalau gagal — panggil dari UI dengan try/catch
     * dan tampilkan pesan errornya, JANGAN diam-diam anggap sukses. */
    suspend fun login(email: String, password: String): Profile {
        val supa = requireClient()
        supa.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val userId = supa.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Login berhasil tapi user id tidak ditemukan.")
        val profile = fetchProfileFromSupabase(userId)
        _currentProfile.value = profile

        // Muat semua data awal, lalu nyalakan realtime supaya list & peta
        // ter-update otomatis selama sesi berjalan.
        refreshAll()
        startRealtimeSync()

        return profile
    }

    suspend fun logout() {
        if (isConfigured) {
            try {
                client.auth.signOut()
            } catch (e: Exception) {
                Log.e(TAG, "Sign out error", e)
            }
        }
        _currentProfile.value = null
        realtimeStarted = false
    }

    private suspend fun fetchProfileFromSupabase(userId: String): Profile {
        return requireClient().postgrest.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingle<Profile>()
    }

    /** Muat ulang semua data referensi & transaksi sekali saja (dipanggil
     * otomatis setelah login, dan bisa dipanggil manual untuk pull-to-refresh).
     *
     * SEBELUMNYA fungsi ini mengambil tabel satu-per-satu secara berurutan
     * TANPA try/catch masing-masing — kalau satu tabel gagal (RLS, kolom
     * hilang, dll), exception-nya menghentikan seluruh fungsi dan semua
     * tabel SETELAHNYA (termasuk lokasi_petugas, paling akhir dalam urutan)
     * jadi ikut tidak pernah kemuat sama sekali. Sekarang tiap tabel
     * independen: satu gagal, yang lain tetap coba dimuat, dan errornya
     * dicatat per tabel supaya gampang dilacak. */
    suspend fun refreshAll() {
        val supa = requireClient()

        suspend fun <T> safeLoad(label: String, block: suspend () -> T) {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // BUKAN error sungguhan — dilempar ulang sesuai aturan
                // structured concurrency, bukan dianggap kegagalan fetch.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "refreshAll: gagal muat $label", e)
            }
        }

        safeLoad("zonas") { _zonas.value = supa.postgrest.from("zonas").select().decodeList() }
        safeLoad("profiles") { _profiles.value = supa.postgrest.from("profiles").select().decodeList() }
        safeLoad("laporan") { _laporan.value = supa.postgrest.from("laporan").select().decodeList() }
        safeLoad("bank_sampah_jenis") { _bankSampahJenis.value = supa.postgrest.from("bank_sampah_jenis").select().decodeList() }
        safeLoad("bank_sampah_transaksi") { _transactions.value = supa.postgrest.from("bank_sampah_transaksi").select().decodeList() }
        safeLoad("chat_pesan") { _chats.value = supa.postgrest.from("chat_pesan").select().decodeList() }
        safeLoad("notifikasi") { _notifikasi.value = supa.postgrest.from("notifikasi").select().decodeList() }
        safeLoad("lokasi_petugas") { _lokasiPetugas.value = supa.postgrest.from("lokasi_petugas").select().decodeList() }

        // Absensi terbaru milik petugas yang login — dipakai untuk mengunci
        // tombol matikan tracking sampai sudah absen keluar. SEBELUMNYA di
        // sini membandingkan tanggal pakai waktu lokal HP ("yyyy-MM-dd")
        // terhadap kolom timestamptz yang tersimpan UTC — di jam-jam awal
        // hari (WIB = UTC+7), tanggal lokal sudah "besok" sementara datanya
        // di database masih tercatat "hari sebelumnya" di UTC, jadi filter
        // tanggal itu selalu meleset dan absensi dianggap tidak ada. Sekarang
        // cukup ambil entri absensi TERBARU (tanpa filter tanggal sama
        // sekali) dan urutkan turun — lebih sederhana dan tidak kena
        // masalah timezone semacam itu.
        val myId = currentProfile.value?.id
        if (myId != null) {
            safeLoad("absensi_terbaru") {
                _myAbsensiHariIni.value = supa.postgrest.from("absensi")
                    .select { filter { eq("petugas_id", myId) }; order("waktu", io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(1) }
                    .decodeList()
            }
        }
    }

    /** Subscribe realtime ke semua tabel yang berubah-ubah selama sesi
     * (lokasi, laporan, chat, notifikasi, transaksi poin, profil/saldo).
     * Setiap ada perubahan, tabel terkait di-fetch ulang dan StateFlow-nya
     * diperbarui — jadi UI yang collectAsState() otomatis ikut berubah. */
    fun startRealtimeSync() {
        if (realtimeStarted || !isConfigured) return
        realtimeStarted = true
        val supa = client

        fun watch(table: String, onChange: suspend () -> Unit) {
            serviceScope.launch {
                try {
                    val ch = supa.realtime.channel("realtime:$table")
                    val flow = ch.postgresChangeFlow<PostgresAction>(schema = "public") { this.table = table }
                    ch.subscribe()
                    flow.collect {
                        try {
                            onChange()
                        } catch (e: Exception) {
                            Log.e(TAG, "Gagal refresh cache setelah perubahan realtime di $table", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal subscribe realtime ke $table", e)
                }
            }
        }

        watch("lokasi_petugas") { _lokasiPetugas.value = client.postgrest.from("lokasi_petugas").select().decodeList() }
        watch("laporan") { _laporan.value = client.postgrest.from("laporan").select().decodeList() }
        watch("chat_pesan") { _chats.value = client.postgrest.from("chat_pesan").select().decodeList() }
        watch("notifikasi") { _notifikasi.value = client.postgrest.from("notifikasi").select().decodeList() }
        watch("bank_sampah_transaksi") { _transactions.value = client.postgrest.from("bank_sampah_transaksi").select().decodeList() }
        watch("profiles") {
            _profiles.value = client.postgrest.from("profiles").select().decodeList()
            // Sinkronkan juga saldo poin akun yang sedang login (mis. setelah admin verifikasi).
            currentProfile.value?.id?.let { id ->
                _profiles.value.firstOrNull { it.id == id }?.let { _currentProfile.value = it }
            }
        }

        // Jaring pengaman: selain realtime websocket di atas, tabel-tabel
        // yang sering berubah juga di-refresh manual tiap 8 detik. Realtime
        // websocket bisa gagal tersambung diam-diam di kondisi tertentu
        // tanpa error yang kelihatan (mis. beda proses/koneksi antara saat
        // Auth login dan saat Realtime pertama connect) — polling ini
        // memastikan peta, chat, dan daftar tugas tetap ter-update walau
        // websocket-nya bermasalah, dengan jeda tunggu maksimal 8 detik.
        serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(8_000)
                try {
                    _lokasiPetugas.value = client.postgrest.from("lokasi_petugas").select().decodeList()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal polling lokasi_petugas", e)
                }
                try {
                    _chats.value = client.postgrest.from("chat_pesan").select().decodeList()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal polling chat_pesan", e)
                }
                try {
                    _laporan.value = client.postgrest.from("laporan").select().decodeList()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal polling laporan", e)
                }
                try {
                    _profiles.value = client.postgrest.from("profiles").select().decodeList()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal polling profiles", e)
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // LOKASI PETUGAS
    // ---------------------------------------------------------------------

    /** Update posisi petugas (dipanggil dari LocationService). Melempar
     * exception kalau gagal, supaya foreground service bisa mencatat/retry
     * alih-alih diam-diam pura-pura berhasil. */
    suspend fun updateLiveLocation(petugasId: String, lat: Double, lng: Double) {
        val payload = LokasiPetugas(petugasId = petugasId, latitude = lat, longitude = lng)
        requireClient().postgrest.from("lokasi_petugas").upsert(payload) {
            onConflict = "petugas_id"
        }
    }

    // ---------------------------------------------------------------------
    // ABSENSI
    // ---------------------------------------------------------------------

    /** Kirim absensi. Melempar exception kalau gagal — UI HARUS menangkap ini
     * dan menampilkan error, karena absensi gagal tersimpan adalah hal kritis. */
    suspend fun submitAbsensi(absensi: Absensi) {
        requireClient().postgrest.from("absensi").insert(absensi)
        try {
            _myAbsensiHariIni.value = client.postgrest.from("absensi")
                .select {
                    filter { eq("petugas_id", absensi.petugasId) }
                    order("waktu", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(1)
                }
                .decodeList()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal refresh absensi terbaru", e)
        }
    }

    // ---------------------------------------------------------------------
    // LAPORAN
    // ---------------------------------------------------------------------

    suspend fun submitLaporan(laporan: Laporan) {
        requireClient().postgrest.from("laporan").insert(laporan)
        _laporan.value = client.postgrest.from("laporan").select().decodeList()
    }

    suspend fun updateLaporanStatus(laporanId: String, status: LaporanStatus, catatan: String? = null) {
        requireClient().postgrest.from("laporan").update({
            Laporan::status setTo status
            if (catatan != null) Laporan::catatanAdmin setTo catatan
        }) {
            filter { eq("id", laporanId) }
        }
        _laporan.value = client.postgrest.from("laporan").select().decodeList()
    }

    // ---------------------------------------------------------------------
    // BANK SAMPAH
    // ---------------------------------------------------------------------

    /** Insert transaksi poin. Untuk TRANSFER_KELUAR, panggil Postgres Function
     * `transfer_poin` di Supabase supaya potong-saldo, tambah-saldo, dan
     * catat 2 baris transaksi terjadi dalam SATU transaction database
     * (atomik) — bukan 2 insert terpisah dari client yang bisa gagal
     * di tengah jalan dan bikin saldo tidak konsisten.
     *
     * Untuk setor sampah / tukar sembako / bayar pajak, transaksi masuk
     * status pending dan poin_saldo baru berubah setelah admin memanggil
     * `verifikasi_transaksi_bank_sampah` (juga RPC atomik) di dashboard. */
    suspend fun submitTransaction(tx: BankSampahTransaksi) {
        val supa = requireClient()

        if (tx.tipe == PoinTxType.TRANSFER_KELUAR) {
            val penerimaId = tx.tujuanWargaId
                ?: throw IllegalArgumentException("Penerima transfer wajib diisi")
            supa.postgrest.rpc(
                "transfer_poin",
                buildJsonObject {
                    put("p_pengirim_id", tx.wargaId)
                    put("p_penerima_id", penerimaId)
                    put("p_jumlah", tx.jumlahPoin)
                    put("p_keterangan", tx.keterangan)
                }
            )
        } else {
            supa.postgrest.from("bank_sampah_transaksi").insert(tx)
        }

        _transactions.value = supa.postgrest.from("bank_sampah_transaksi").select().decodeList()
        currentProfile.value?.let { _currentProfile.value = fetchProfileFromSupabase(it.id) }
    }

    // ---------------------------------------------------------------------
    // CHAT
    // ---------------------------------------------------------------------

    suspend fun sendChat(pesan: ChatPesan) {
        requireClient().postgrest.from("chat_pesan").insert(pesan)
        _chats.value = client.postgrest.from("chat_pesan").select().decodeList()
    }

    // ---------------------------------------------------------------------
    // STORAGE
    // ---------------------------------------------------------------------

    /** Upload foto. Melempar exception kalau gagal — jangan pernah kembalikan
     * URL gambar palsu/placeholder seolah upload berhasil. */
    suspend fun uploadPhoto(bucketName: String, path: String, bytes: ByteArray): String {
        val storageClient = requireClient().storage
        storageClient.from(bucketName).upload(path, bytes)
        return storageClient.from(bucketName).publicUrl(path)
    }
}
