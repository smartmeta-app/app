package id.teladanbarat.smartmeta.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import id.teladanbarat.smartmeta.data.*
import id.teladanbarat.smartmeta.ui.components.NavItem
import id.teladanbarat.smartmeta.ui.components.SmartMetaBottomNav
import id.teladanbarat.smartmeta.ui.components.SmartMetaTopBar
import id.teladanbarat.smartmeta.ui.components.StatusPill
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WargaScreen(
    profile: Profile,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(0) } // 0: Map & Officers, 1: Buat Laporan, 2: Bank Sampah, 3: Pesan Chat, 4: Notifikasi

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SmartMetaTopBar(
            title = "SMART META Warga",
            subtitle = "${profile.nama} · Saldo ${profile.poinSaldo} Poin",
            onLogout = onLogout
        )

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> WargaMapTab(profile = profile)
                1 -> WargaLaporanTab(profile = profile)
                2 -> WargaBankSampahTab(profile = profile)
                3 -> WargaChatTab(profile = profile)
                4 -> WargaNotifikasiTab(profile = profile)
            }
        }

        SmartMetaBottomNav(
            items = listOf(
                NavItem(Icons.Default.Map, "Peta"),
                NavItem(Icons.Default.ReportProblem, "Lapor"),
                NavItem(Icons.Default.Savings, "Bank"),
                NavItem(Icons.Default.Chat, "Pesan"),
                NavItem(Icons.Default.Notifications, "Notif")
            ),
            selectedIndex = activeTab,
            onSelect = { activeTab = it }
        )
    }
}

// ------------------- TAB 1: PETA LOKASI PETUGAS TERDEKAT (OSMDROID) -------------------
@Composable
fun WargaMapTab(profile: Profile) {
    val context = LocalContext.current
    val liveLocations by SupabaseService.lokasiPetugas.collectAsState()
    val allProfiles by SupabaseService.profiles.collectAsState()
    var mapLoadError by remember { mutableStateOf(false) }

    // Lokasi warga sungguhan dari GPS HP-nya — dipakai untuk pusat peta dan
    // menghitung jarak ke tiap petugas. SEBELUMNYA kode ini filter petugas
    // berdasarkan kecocokan zona_id, padahal dashboard admin tidak punya
    // form untuk set zona ke akun petugas/warga — jadi begitu salah satu
    // di-set manual, semua petugas hilang dari peta. Sekarang diganti:
    // tampilkan SEMUA petugas yang lokasinya sedang live, diurutkan dari
    // yang paling dekat ke warga (sesuai jarak GPS asli), tidak lagi
    // bergantung pada zona.
    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            try {
                locationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) myLocation = GeoPoint(loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) { /* permission dicabut di tengah jalan, abaikan */ }
        } else {
            Toast.makeText(context, "Izin lokasi ditolak. Peta memakai titik default.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            try {
                locationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) myLocation = GeoPoint(loc.latitude, loc.longitude)
                }
            } catch (e: SecurityException) { /* abaikan */ }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Titik jatuh terakhir kalau GPS warga belum berhasil didapat sama sekali
    // (misal izin belum diberikan) — dekat Medan, bukan Jakarta seperti kode lama.
    val fallbackLat = -6.1751
    val fallbackLng = 106.8650 // TODO: ganti ke koordinat Kelurahan Teladan Barat sungguhan kalau sudah tahu titik pastinya
    val centerLat = myLocation?.latitude ?: fallbackLat
    val centerLng = myLocation?.longitude ?: fallbackLng

    val activePetugas = allProfiles.filter { it.role == UserRole.PETUGAS }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    // Petugas + jarak ke warga, diurutkan dari yang paling dekat
    val petugasWithDistance = liveLocations.mapNotNull { loc ->
        val petProfile = activePetugas.firstOrNull { it.id == loc.petugasId }
        if (petProfile != null) {
            val distance = haversineMeters(centerLat, centerLng, loc.latitude, loc.longitude)
            Triple(petProfile, loc, distance)
        } else null
    }.sortedBy { it.third }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Lacak Petugas Kebersihan Terdekat",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (petugasWithDistance.isEmpty()) {
                        "Belum ada petugas yang sedang aktif melacak lokasi."
                    } else {
                        val nearest = petugasWithDistance.first()
                        "Terdekat: ${nearest.first.nama} (~${(nearest.third / 1000).let { if (it < 1) "${nearest.third.toInt()} m" else "${"%.1f".format(it)} km" }})"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        ) {
            if (!mapLoadError) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(16.5)
                                controller.setCenter(GeoPoint(centerLat, centerLng))
                            }
                        } catch (e: Exception) {
                            mapLoadError = true
                            MapView(ctx)
                        }
                    },
                    update = { mapView ->
                        try {
                            mapView.overlays.clear()

                            // Marker posisi warga sendiri (titik biru, pusat peta ikut ini)
                            val myMarker = Marker(mapView).apply {
                                position = GeoPoint(centerLat, centerLng)
                                title = if (myLocation != null) "Lokasi Anda" else "Titik Default (izin lokasi belum aktif)"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            mapView.overlays.add(myMarker)

                            // Kalau lokasi warga berubah, geser pusat peta ikut — supaya
                            // peta selalu berpusat di posisi warga saat ini, bukan diam
                            // di satu titik zona statis seperti sebelumnya.
                            mapView.controller.setCenter(GeoPoint(centerLat, centerLng))

                            petugasWithDistance.forEach { (petProfile, loc, distance) ->
                                val distanceLabel = if (distance < 1000) "${distance.toInt()} m" else "${"%.1f".format(distance / 1000)} km"
                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(loc.latitude, loc.longitude)
                                    title = "${petProfile.nama} (${petProfile.jenisPetugas?.name ?: "-"})"
                                    subDescription = "Jarak: $distanceLabel · HP: ${petProfile.noHp ?: "-"}"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                mapView.overlays.add(marker)
                            }
                            mapView.invalidate()
                        } catch (e: Exception) {
                            mapLoadError = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.Explore, contentDescription = "Compass", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Visualisasi Koordinat Peta Aktif",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Lokasi Anda: ($centerLat, $centerLng)", fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Daftar Petugas Aktif terlacak:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        petugasWithDistance.forEach { (pet, loc, distance) ->
                            val distanceLabel = if (distance < 1000) "${distance.toInt()} m" else "${"%.1f".format(distance / 1000)} km"
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Engineering, contentDescription = "Petugas", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(pet.nama, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("Jarak: $distanceLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


// ------------------- TAB 2: BUAT LAPORAN WARGA (FOTO & GEOTAGGING) -------------------
@Composable
fun WargaLaporanTab(profile: Profile) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showCamera by remember { mutableStateOf(false) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var jenisLaporan by remember { mutableStateOf("") }
    var deskripsi by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    if (showCamera) {
        CameraView(
            isFrontCamera = false,
            onImageCaptured = { file ->
                capturedPhotoFile = file
                showCamera = false
            },
            onClose = { showCamera = false }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Laporkan Masalah Kebersihan",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item {
            OutlinedTextField(
                value = jenisLaporan,
                onValueChange = { jenisLaporan = it },
                label = { Text("Jenis Laporan / Masalah") },
                placeholder = { Text("mis: Sampah Menumpuk, Salokan Tersumbat") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            OutlinedTextField(
                value = deskripsi,
                onValueChange = { deskripsi = it },
                label = { Text("Detail Deskripsi") },
                placeholder = { Text("Jelaskan rincian dan lokasi pasti tumpukan sampah...") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().height(200.dp).clickable { showCamera = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (capturedPhotoFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = capturedPhotoFile),
                            contentDescription = "Foto Bukti Laporan",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddPhotoAlternate,
                                contentDescription = "Add Photo",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Klik untuk Ambil Foto Bukti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (jenisLaporan.isBlank() || capturedPhotoFile == null) {
                        Toast.makeText(context, "Masalah & foto bukti wajib dilampirkan!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Izin lokasi diperlukan untuk mengirim laporan warga.", Toast.LENGTH_SHORT).show()
                        isLoading = false
                        return@Button
                    }

                    try {
                        locationClient.lastLocation.addOnSuccessListener { location ->
                            val lat = location?.latitude
                            val lng = location?.longitude

                            coroutineScope.launch {
                                try {
                                    val photoBytes = capturedPhotoFile!!.readBytes()
                                    val photoUrl = SupabaseService.uploadPhoto("foto-laporan", "lap_warga_${profile.id}_${System.currentTimeMillis()}.jpg", photoBytes)

                                    val newLaporan = Laporan(
                                        pelaporId = profile.id,
                                        jenis = jenisLaporan,
                                        deskripsi = deskripsi,
                                        fotoUrl = photoUrl,
                                        latitude = lat,
                                        longitude = lng,
                                        status = LaporanStatus.BARU
                                    )

                                    SupabaseService.submitLaporan(newLaporan)
                                    isLoading = false
                                    Toast.makeText(context, "Laporan Anda berhasil dikirim ke petugas!", Toast.LENGTH_LONG).show()
                                    jenisLaporan = ""
                                    deskripsi = ""
                                    capturedPhotoFile = null
                                } catch (e: Exception) {
                                    isLoading = false
                                    Toast.makeText(context, "Gagal mengirim laporan: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.addOnFailureListener {
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Kirim Laporan Kebersihan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ------------------- TAB 3: BANK SAMPAH WARGA (SALDO, TRANSAKSI, SETOR, PENUKARAN, TRANSFER) -------------------
@Composable
fun WargaBankSampahTab(profile: Profile) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val wasteTypes by SupabaseService.bankSampahJenis.collectAsState()
    val transactions by SupabaseService.transactions.collectAsState()
    val allProfiles by SupabaseService.profiles.collectAsState()

    var activeForm by remember { mutableStateOf(0) } // 0: Menu/Tx History, 1: Form Setor, 2: Form Tukar, 3: Form Transfer

    // Setor Sampah Form inputs
    var selectedWasteTypeId by remember { mutableStateOf("bs-1") }
    var weightInput by remember { mutableStateOf("") }

    // Redeem Form inputs
    var selectedRedeemType by remember { mutableStateOf(PoinTxType.TUKAR_SEMBAKO) }
    var redeemPointsInput by remember { mutableStateOf("") }
    var redeemDesc by remember { mutableStateOf("") }

    // Transfer Form inputs
    var targetResidentId by remember { mutableStateOf("") }
    var transferPointsInput by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }

    when (activeForm) {
        0 -> {
            // MAIN BANK SAMPAH HOMEPAGE
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Balance card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                            Text("SALDO TABUNGAN SAMPAH", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${profile.poinSaldo} POIN", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nilai Poin dapat ditukarkan dengan Sembako atau digunakan untuk pembayaran Pajak Daerah.",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Action Menu Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { activeForm = 1 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Setor")
                                Text("Setor Sampah", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { activeForm = 2 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Storefront, contentDescription = "Tukar")
                                Text("Tukar Poin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { activeForm = 3 },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.SendToMobile, contentDescription = "Transfer")
                                Text("Transfer Poin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Transactions Ledger Header
                item {
                    Text(
                        "Riwayat Transaksi Tabungan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val myTransactions = transactions.filter { it.wargaId == profile.id }

                if (myTransactions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat transaksi tabungan sampah.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    items(myTransactions) { tx ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (tx.tipe) {
                                        PoinTxType.SETOR_SAMPAH -> Icons.Default.AddCircle
                                        PoinTxType.TUKAR_SEMBAKO -> Icons.Default.LocalMall
                                        PoinTxType.BAYAR_PAJAK -> Icons.Default.ReceiptLong
                                        PoinTxType.TRANSFER_KELUAR -> Icons.Default.ArrowCircleUp
                                        PoinTxType.TRANSFER_MASUK -> Icons.Default.ArrowCircleDown
                                    }

                                    val iconColor = when (tx.tipe) {
                                        PoinTxType.SETOR_SAMPAH, PoinTxType.TRANSFER_MASUK -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.error
                                    }

                                    Icon(icon, contentDescription = tx.tipe.name, tint = iconColor, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(tx.keterangan ?: tx.tipe.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(tx.createdAt?.take(10) ?: "Baru saja", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }

                                val pointsPrefix = when (tx.tipe) {
                                    PoinTxType.SETOR_SAMPAH, PoinTxType.TRANSFER_MASUK -> "+"
                                    else -> "-"
                                }

                                Text(
                                    text = "$pointsPrefix${tx.jumlahPoin} Poin",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (pointsPrefix == "+") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        1 -> {
            // FORM SETOR SAMPAH
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activeForm = 0 }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Text("Ajukan Setor Sampah", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }

                Text("Pilih Jenis Sampah:", fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(wasteTypes) { waste ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedWasteTypeId = waste.id ?: "bs-1" }
                                .background(if (selectedWasteTypeId == waste.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(8.dp)
                        ) {
                            RadioButton(selected = selectedWasteTypeId == waste.id, onClick = { selectedWasteTypeId = waste.id ?: "bs-1" })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(waste.namaSampah, fontWeight = FontWeight.SemiBold)
                                Text("${waste.poinPerKg} Poin / Kilogram", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Berat Sampah (Kg)") },
                    placeholder = { Text("mis: 2.5") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val weight = weightInput.toDoubleOrNull()
                        if (weight == null || weight <= 0) {
                            Toast.makeText(context, "Masukkan berat sampah yang valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        val selectedWaste = wasteTypes.firstOrNull { it.id == selectedWasteTypeId } ?: wasteTypes.first()
                        val calculatedPoints = (selectedWaste.poinPerKg * weight).toInt()

                        val newTx = BankSampahTransaksi(
                            wargaId = profile.id,
                            tipe = PoinTxType.SETOR_SAMPAH,
                            jumlahPoin = calculatedPoints,
                            beratKg = weight,
                            jenisSampahId = selectedWaste.id,
                            keterangan = "Setor ${selectedWaste.namaSampah} ${weight} Kg"
                        )

                        coroutineScope.launch {
                            try {
                                SupabaseService.submitTransaction(newTx)
                                isLoading = false
                                Toast.makeText(context, "Setoran dikirim, menunggu verifikasi admin. Estimasi +$calculatedPoints Poin.", Toast.LENGTH_LONG).show()
                                activeForm = 0
                                weightInput = ""
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Gagal mengirim setoran: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Kirim Setoran")
                }
            }
        }

        2 -> {
            // FORM PENUKARAN POIN (TUKAR SEMBAKO ATAU BAYAR PAJAK)
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activeForm = 0 }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Text("Tukarkan Poin Tabungan", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(selected = selectedRedeemType == PoinTxType.TUKAR_SEMBAKO, onClick = { selectedRedeemType = PoinTxType.TUKAR_SEMBAKO })
                        Text("Tukar Sembako")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        RadioButton(selected = selectedRedeemType == PoinTxType.BAYAR_PAJAK, onClick = { selectedRedeemType = PoinTxType.BAYAR_PAJAK })
                        Text("Bayar Pajak")
                    }
                }

                OutlinedTextField(
                    value = redeemPointsInput,
                    onValueChange = { redeemPointsInput = it },
                    label = { Text("Jumlah Poin yang Ditukarkan") },
                    placeholder = { Text("mis: 200") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = redeemDesc,
                    onValueChange = { redeemDesc = it },
                    label = { Text("Keterangan Tambahan") },
                    placeholder = { Text("mis: Tukar beras 2 kg / Bayar PBB No. 12") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val points = redeemPointsInput.toIntOrNull()
                        if (points == null || points <= 0) {
                            Toast.makeText(context, "Jumlah poin tidak valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (profile.poinSaldo < points) {
                            Toast.makeText(context, "Saldo Poin Anda tidak mencukupi!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        val newTx = BankSampahTransaksi(
                            wargaId = profile.id,
                            tipe = selectedRedeemType,
                            jumlahPoin = points,
                            keterangan = if (selectedRedeemType == PoinTxType.TUKAR_SEMBAKO) "Penukaran Sembako: $redeemDesc" else "Pembayaran Pajak: $redeemDesc"
                        )

                        coroutineScope.launch {
                            try {
                                SupabaseService.submitTransaction(newTx)
                                isLoading = false
                                Toast.makeText(context, "Pengajuan penukaran dikirim, menunggu verifikasi admin.", Toast.LENGTH_SHORT).show()
                                activeForm = 0
                                redeemPointsInput = ""
                                redeemDesc = ""
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Gagal mengajukan penukaran: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Tukarkan Poin")
                }
            }
        }

        3 -> {
            // FORM TRANSFER POIN KE SESAMA WARGA
            val otherResidents = allProfiles.filter { it.role == UserRole.WARGA && it.id != profile.id }

            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { activeForm = 0 }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    Text("Transfer Poin", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }

                Text("Pilih Penerima (Warga):", fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(otherResidents) { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { targetResidentId = res.id }
                                .background(if (targetResidentId == res.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(8.dp)
                        ) {
                            RadioButton(selected = targetResidentId == res.id, onClick = { targetResidentId = res.id })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(res.nama, fontWeight = FontWeight.SemiBold)
                                Text(res.noHp ?: "Tidak ada telepon", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = transferPointsInput,
                    onValueChange = { transferPointsInput = it },
                    label = { Text("Jumlah Poin untuk Ditransfer") },
                    placeholder = { Text("mis: 150") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = {
                        val points = transferPointsInput.toIntOrNull()
                        if (targetResidentId.isEmpty()) {
                            Toast.makeText(context, "Pilih warga penerima transfer!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (points == null || points <= 0) {
                            Toast.makeText(context, "Jumlah poin transfer tidak valid!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (profile.poinSaldo < points) {
                            Toast.makeText(context, "Saldo Poin Anda tidak mencukupi!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        val targetProfile = otherResidents.firstOrNull { it.id == targetResidentId }
                        val newTx = BankSampahTransaksi(
                            wargaId = profile.id,
                            tipe = PoinTxType.TRANSFER_KELUAR,
                            jumlahPoin = points,
                            tujuanWargaId = targetResidentId,
                            keterangan = "Transfer poin ke ${targetProfile?.nama}"
                        )

                        coroutineScope.launch {
                            try {
                                SupabaseService.submitTransaction(newTx)
                                isLoading = false
                                Toast.makeText(context, "Transfer poin berhasil! -$points Poin.", Toast.LENGTH_SHORT).show()
                                activeForm = 0
                                transferPointsInput = ""
                                targetResidentId = ""
                            } catch (e: Exception) {
                                isLoading = false
                                Toast.makeText(context, "Gagal transfer poin: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White) else Text("Kirim Transfer Poin")
                }
            }
        }
    }
}


// ------------------- TAB 4: PESAN CHAT REALTIME -------------------
@Composable
fun WargaChatTab(profile: Profile) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allChats by SupabaseService.chats.collectAsState()
    val allProfiles by SupabaseService.profiles.collectAsState()

    var activeContact by remember { mutableStateOf<Profile?>(null) }
    var chatMessageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    if (activeContact != null) {
        val filteredChats = allChats.filter {
            (it.pengirimId == profile.id && it.penerimaId == activeContact!!.id) ||
            (it.pengirimId == activeContact!!.id && it.penerimaId == profile.id)
        }

        LaunchedEffect(filteredChats.size) {
            if (filteredChats.isNotEmpty()) {
                listState.animateScrollToItem(filteredChats.size - 1)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Chat Header
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeContact = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(activeContact!!.nama, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Petugas Kebersihan Kelurahan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            // Message Bubble list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredChats) { chat ->
                    val isMyMsg = chat.pengirimId == profile.id
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMyMsg) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMyMsg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMyMsg) 18.dp else 4.dp,
                                bottomEnd = if (isMyMsg) 4.dp else 18.dp
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = chat.pesan ?: "",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                                color = if (isMyMsg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Chat Send box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = chatMessageText,
                    onValueChange = { chatMessageText = it },
                    placeholder = { Text("Ketik pesan...") },
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                IconButton(
                    onClick = {
                        if (chatMessageText.isNotBlank()) {
                            val newMsg = ChatPesan(
                                pengirimId = profile.id,
                                penerimaId = activeContact!!.id,
                                pesan = chatMessageText
                            )
                            coroutineScope.launch {
                                try {
                                    SupabaseService.sendChat(newMsg)
                                    chatMessageText = ""
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal mengirim pesan: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    } else {
        // List of all active officers in the citizen's zone
        val officersInZone = allProfiles.filter { it.role == UserRole.PETUGAS && it.zonaId == profile.zonaId }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Pesan ke Petugas Lapangan",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (officersInZone.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Belum ada petugas aktif di sektor Anda.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(officersInZone) { officer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().clickable { activeContact = officer },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Engineering,
                                    contentDescription = "Petugas Avatar",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(officer.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Petugas ${officer.jenisPetugas?.name ?: ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ------------------- TAB 5: BROADCAST NOTIFIKASI DARI KELURAHAN -------------------
@Composable
fun WargaNotifikasiTab(profile: Profile) {
    val broadcastNotif by SupabaseService.notifikasi.collectAsState()

    // Filter notifications: general target (role = warga) OR specific to their zone
    val filteredNotif = broadcastNotif.filter {
        (it.targetRole == null || it.targetRole == UserRole.WARGA) &&
        (it.targetZonaId == null || it.targetZonaId == profile.zonaId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Pengumuman Kelurahan Teladan Barat",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (filteredNotif.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Belum ada pengumuman baru untuk Sektor Anda.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(filteredNotif) { notif ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Campaign, contentDescription = "Announcement", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = notif.judul,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notif.isi,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dikirim pada: ${notif.createdAt?.take(10) ?: "Hari ini"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
