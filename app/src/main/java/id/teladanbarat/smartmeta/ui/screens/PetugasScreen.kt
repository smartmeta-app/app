package id.teladanbarat.smartmeta.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import id.teladanbarat.smartmeta.data.*
import id.teladanbarat.smartmeta.service.LocationService
import id.teladanbarat.smartmeta.ui.components.NavItem
import id.teladanbarat.smartmeta.ui.components.SmartMetaBottomNav
import id.teladanbarat.smartmeta.ui.components.SmartMetaTopBar
import id.teladanbarat.smartmeta.ui.components.StatusPill
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetugasScreen(
    profile: Profile,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(0) } // 0: Home/Track, 1: Absensi, 2: Laporan Kerja, 3: Chat Box

    // Track Foreground Service State locally
    var isTrackingEnabled by remember { mutableStateOf(LocationService.isRunning) }

    // Navigation and screen frame — pakai komponen custom SMART META, bukan
    // Scaffold + TopAppBar + NavigationBar bawaan template Material biasa.
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SmartMetaTopBar(
            title = "SMART META Petugas",
            subtitle = "${profile.nama} · ${profile.jenisPetugas?.name ?: "Petugas"}",
            onLogout = onLogout
        )

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> PetugasDashboardTab(
                    profile = profile,
                    isTrackingEnabled = isTrackingEnabled,
                    onTrackingChanged = { isTrackingEnabled = it }
                )
                1 -> PetugasAbsensiTab(profile = profile)
                2 -> PetugasLaporanTab(profile = profile)
                3 -> PetugasChatTab(profile = profile)
            }
        }

        SmartMetaBottomNav(
            items = listOf(
                NavItem(Icons.Default.Dashboard, "Utama"),
                NavItem(Icons.Default.HowToReg, "Absen"),
                NavItem(Icons.Default.AddPhotoAlternate, "Lapor"),
                NavItem(Icons.Default.Chat, "Chat")
            ),
            selectedIndex = activeTab,
            onSelect = { activeTab = it }
        )
    }
}

// ------------------- TAB 1: DASHBOARD & ACTIVE TASKS -------------------
@Composable
fun PetugasDashboardTab(
    profile: Profile,
    isTrackingEnabled: Boolean,
    onTrackingChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allLaporan by SupabaseService.laporan.collectAsState()
    val zonasList by SupabaseService.zonas.collectAsState()

    val myZone = zonasList.firstOrNull { it.id == profile.zonaId }
    val assignedLaporan = allLaporan.filter { it.petugasId == profile.id || it.petugasId == null }

    // Begitu petugas menyalakan tracking, tombol Matikan langsung TERKUNCI
    // dan baru bisa dipakai lagi setelah dia absen KELUAR. Sebelumnya kunci
    // ini cuma aktif kalau status absen terakhir "masuk" — sekarang kuncinya
    // berlaku selama tracking menyala, apa pun riwayat absennya, dan cuma
    // lepas kalau entri absen PALING TERAKHIR berstatus "keluar".
    val myAbsensiHariIni by SupabaseService.myAbsensiHariIni.collectAsState()
    val sortedAbsensi = myAbsensiHariIni.sortedBy { it.waktu ?: "" }
    val sudahAbsenKeluar = sortedAbsensi.lastOrNull()?.status == ShiftStatus.KELUAR
    val trackingLocked = isTrackingEnabled && !sudahAbsenKeluar

    // Location Permission Launchers
    val locationPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            // Permission granted, start service
            LocationService.startService(context)
            onTrackingChanged(true)
            Toast.makeText(context, "Pelacakan lokasi aktif untuk shift kerja", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Akses lokasi ditolak! Tidak bisa melacak lokasi.", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcoming card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Eco,
                            contentDescription = "Eco",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ayo Jaga Kebersihan Kelurahan!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Setiap langkah pembersihan yang Anda lakukan sangat berarti bagi warga Teladan Barat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Location Service Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Sistem Pelacak Lokasi (Foreground Service)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Melacak dan membagikan lokasi Anda ke warga saat sedang bertugas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isTrackingEnabled) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                text = if (isTrackingEnabled) "MELACAK" else "TIDAK MELACAK",
                                color = if (isTrackingEnabled) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }

                        Button(
                            onClick = {
                                if (isTrackingEnabled) {
                                    if (trackingLocked) {
                                        Toast.makeText(context, "Tidak bisa mematikan pelacak sebelum absen keluar.", Toast.LENGTH_LONG).show()
                                        return@Button
                                    }
                                    LocationService.stopService(context)
                                    onTrackingChanged(false)
                                    Toast.makeText(context, "Pelacakan dinonaktifkan.", Toast.LENGTH_SHORT).show()
                                } else {
                                    // Check permission
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine) {
                                        LocationService.startService(context)
                                        onTrackingChanged(true)
                                        Toast.makeText(context, "Pelacakan diaktifkan.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        locationPermissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            },
                            enabled = !trackingLocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrackingEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isTrackingEnabled) "Matikan" else "Aktifkan")
                        }
                    }

                    if (trackingLocked) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Terkunci sampai Anda absen keluar",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }

                    if (isTrackingEnabled) {
                        val lastStatus by LocationService.lastUpdateStatus.collectAsState()
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GpsFixed,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lastStatus,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ignore Battery Optimization request
                    TextButton(
                        onClick = {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = Uri.parse("package:${context.packageName}")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Fitur optimasi baterai tidak didukung.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.BatterySaver, contentDescription = "Battery")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kecualikan dari Optimasi Baterai (Rekomendasi)", fontSize = 12.sp)
                    }
                }
            }
        }

        // Zone Info Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = "Map Zone",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Wilayah Penugasan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = myZone?.namaZona ?: "Semua Sektor",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Radius Operasi: ${myZone?.radiusMeter ?: 150} meter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Assigned jobs Header
        item {
            Text(
                text = "Tugas & Laporan Lapangan (${assignedLaporan.size})",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // List of Laporan
        if (assignedLaporan.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tidak ada laporan pekerjaan aktif saat ini.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(assignedLaporan) { laporan ->
                LaporanItemRow(laporan)
            }
        }
    }
}

@Composable
fun LaporanItemRow(laporan: Laporan) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(laporan.jenis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        laporan.createdAt?.take(10) ?: "Hari ini",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                val statusColor = when (laporan.status) {
                    LaporanStatus.BARU -> MaterialTheme.colorScheme.primary
                    LaporanStatus.DIPROSES -> MaterialTheme.colorScheme.tertiary
                    LaporanStatus.SELESAI -> MaterialTheme.colorScheme.secondary
                    LaporanStatus.DITOLAK -> MaterialTheme.colorScheme.error
                }

                StatusPill(text = laporan.status.name, color = statusColor)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = laporan.deskripsi ?: "Tidak ada deskripsi.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2
            )

            if (isExpanded) {
                if (!laporan.fotoUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        painter = rememberAsyncImagePainter(model = laporan.fotoUrl),
                        contentDescription = "Foto Laporan",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                if (laporan.latitude != null && laporan.longitude != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Koordinat Lokasi: ${laporan.latitude}, ${laporan.longitude}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action controls for petugas
                if (laporan.status == LaporanStatus.BARU || laporan.status == LaporanStatus.DIPROSES) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (laporan.status == LaporanStatus.BARU) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            SupabaseService.updateLaporanStatus(laporan.id ?: "", LaporanStatus.DIPROSES)
                                            Toast.makeText(context, "Laporan disetujui untuk diproses!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Proses Tugas", fontSize = 12.sp)
                            }
                        } else if (laporan.status == LaporanStatus.DIPROSES) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            SupabaseService.updateLaporanStatus(laporan.id ?: "", LaporanStatus.SELESAI, "Selesai dibersihkan oleh petugas")
                                            Toast.makeText(context, "Pekerjaan diselesaikan!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                            ) {
                                Text("Selesaikan Tugas", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        SupabaseService.updateLaporanStatus(laporan.id ?: "", LaporanStatus.DITOLAK, "Ditolak oleh petugas")
                                        Toast.makeText(context, "Laporan ditolak.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal update status: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Tolak", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}


// ------------------- TAB 2: ABSENSI DENGAN CAMERAX & GPS -------------------
@Composable
fun PetugasAbsensiTab(profile: Profile) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showCamera by remember { mutableStateOf(false) }
    var capturedPhotoFile by remember { mutableStateOf<File?>(null) }
    var selectedStatus by remember { mutableStateOf(ShiftStatus.MASUK) }
    var isLoading by remember { mutableStateOf(false) }

    val zonasList by SupabaseService.zonas.collectAsState()
    val myZone = zonasList.firstOrNull { it.id == profile.zonaId }

    // Cek apakah waktu sekarang masih dalam jam kerja yang di-set admin di
    // dashboard. Kalau admin belum pernah set (kolomnya kosong/null), pakai
    // jam kerja default 07:00–15:00 (sama seperti nilai default di form
    // dashboard) supaya tetap ada batasan yang wajar.
    val isWithinWorkHours = remember(profile.jamKerjaMulai, profile.jamKerjaSelesai) {
        checkWithinWorkHours(profile.jamKerjaMulai, profile.jamKerjaSelesai)
    }
    val jamMulaiDisplay = profile.jamKerjaMulai?.take(5) ?: "07:00"
    val jamSelesaiDisplay = profile.jamKerjaSelesai?.take(5) ?: "15:00"

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(context, "Akses kamera ditolak! Tidak bisa selfie.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showCamera) {
        CameraView(
            isFrontCamera = true,
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
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Absensi Kehadiran Petugas",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        }

        if (!isWithinWorkHours) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Di luar jam kerja",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                            Text(
                                "Absensi hanya bisa dilakukan pukul $jamMulaiDisplay–$jamSelesaiDisplay. Hubungi admin kalau jadwal ini salah.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            // Select status
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { selectedStatus = ShiftStatus.MASUK },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == ShiftStatus.MASUK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Shift MASUK", color = if (selectedStatus == ShiftStatus.MASUK) Color.White else Color.Black)
                }

                Button(
                    onClick = { selectedStatus = ShiftStatus.KELUAR },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == ShiftStatus.KELUAR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Shift KELUAR", color = if (selectedStatus == ShiftStatus.KELUAR) Color.White else Color.Black)
                }
            }
        }

        item {
            // Display captured image or selfie prompt
            Card(
                modifier = Modifier.size(200.dp).clickable {
                    val cameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (cameraPerm == PackageManager.PERMISSION_GRANTED) {
                        showCamera = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (capturedPhotoFile != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = capturedPhotoFile),
                            contentDescription = "Selfie Absen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = "Add Selfie",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Klik untuk Ambil Selfie", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            // Submit Absensi button
            Button(
                onClick = {
                    if (!isWithinWorkHours) {
                        Toast.makeText(context, "Di luar jam kerja ($jamMulaiDisplay–$jamSelesaiDisplay). Absensi ditolak.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (capturedPhotoFile == null) {
                        Toast.makeText(context, "Ambil selfie terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    // Try to get GPS Location
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Aktifkan izin lokasi untuk absen!", Toast.LENGTH_SHORT).show()
                        isLoading = false
                        return@Button
                    }

                    try {
                        locationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val lat = location.latitude
                                val lng = location.longitude

                                // Calculate distance to Zone Center (Haversine)
                                val zoneLat = myZone?.latitude ?: -6.1751
                                val zoneLng = myZone?.longitude ?: 106.8650
                                val zoneRadius = myZone?.radiusMeter ?: 150

                                val distance = calculateHaversineDistance(lat, lng, zoneLat, zoneLng)
                                val inRadius = distance <= zoneRadius

                                coroutineScope.launch {
                                    try {
                                        val photoBytes = capturedPhotoFile!!.readBytes()
                                        val photoUrl = SupabaseService.uploadPhoto("foto-absensi", "absen_${profile.id}_${System.currentTimeMillis()}.jpg", photoBytes)

                                        val absensi = Absensi(
                                            petugasId = profile.id,
                                            status = selectedStatus,
                                            latitude = lat,
                                            longitude = lng,
                                            fotoSelfieUrl = photoUrl,
                                            dalamRadius = inRadius,
                                            waktu = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(Date())
                                        )

                                        SupabaseService.submitAbsensi(absensi)
                                        isLoading = false
                                        val locationStatusMsg = if (inRadius) "DI DALAM ZONA" else "DI LUAR ZONA (${distance.toInt()}m)"
                                        Toast.makeText(context, "Absensi berhasil disimpan! Status: $locationStatusMsg", Toast.LENGTH_LONG).show()
                                        capturedPhotoFile = null
                                    } catch (e: Exception) {
                                        isLoading = false
                                        Toast.makeText(context, "Gagal menyimpan absensi: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Gagal mendapatkan GPS. Pastikan GPS menyala.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: SecurityException) {
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading && isWithinWorkHours
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else if (!isWithinWorkHours) {
                    Text("Di Luar Jam Kerja", fontWeight = FontWeight.Bold)
                } else {
                    Text("Kirim Absensi Kehadiran", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** true kalau waktu sekarang (di HP petugas) ada di antara jam_kerja_mulai
 * dan jam_kerja_selesai yang di-set admin. Formatnya dari Postgres bisa
 * "HH:mm:ss" atau "HH:mm", jadi diambil jam & menitnya saja lewat parsing
 * manual (BUKAN java.time.LocalTime, karena itu baru native tersedia mulai
 * Android 8.0/API 26 — app ini minSdk 24, jadi wajib hindari supaya tidak
 * crash di HP Android 7). Kalau admin belum pernah set (null), pakai
 * default 07:00–15:00. */
fun checkWithinWorkHours(mulai: String?, selesai: String?): Boolean {
    fun toMinutes(raw: String): Int? {
        val parts = raw.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }

    return try {
        val startMin = toMinutes(mulai ?: "07:00") ?: 7 * 60
        val endMin = toMinutes(selesai ?: "15:00") ?: 15 * 60
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        if (startMin <= endMin) {
            nowMin in startMin..endMin
        } else {
            // Shift lewat tengah malam (mis. 22:00–06:00)
            nowMin >= startMin || nowMin <= endMin
        }
    } catch (e: Exception) {
        // Kalau format waktu di database tidak terduga, jangan blokir petugas
        // gara-gara bug parsing — lebih aman izinkan daripada bikin semua
        // petugas tidak bisa absen sama sekali.
        true
    }
}

// Haversine Distance Calculation (Meters)
fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0 // Earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}


// ------------------- TAB 3: KIRIM LAPORAN KERJA -------------------
@Composable
fun PetugasLaporanTab(profile: Profile) {
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
                text = "Kirim Laporan Kerja Harian",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        item {
            OutlinedTextField(
                value = jenisLaporan,
                onValueChange = { jenisLaporan = it },
                label = { Text("Jenis Pekerjaan / Kegiatan") },
                placeholder = { Text("mis: Pembersihan Selokan, Penyortiran Kompos") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item {
            OutlinedTextField(
                value = deskripsi,
                onValueChange = { deskripsi = it },
                label = { Text("Deskripsi Pekerjaan") },
                placeholder = { Text("Tulis rincian aktivitas pembersihan dan sampah yang diangkut...") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
        }

        item {
            // Photo Preview card
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
                            contentDescription = "Foto Laporan Kerja",
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
                            Text("Klik untuk Ambil Foto Kegiatan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (jenisLaporan.isBlank() || capturedPhotoFile == null) {
                        Toast.makeText(context, "Jenis kegiatan & foto wajib dilampirkan!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    // Request GPS coordinates
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Izin lokasi diperlukan untuk mengirim laporan.", Toast.LENGTH_SHORT).show()
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
                                    val photoUrl = SupabaseService.uploadPhoto("foto-laporan", "lap_${profile.id}_${System.currentTimeMillis()}.jpg", photoBytes)

                                    val newLaporan = Laporan(
                                        petugasId = profile.id,
                                        jenis = jenisLaporan,
                                        deskripsi = deskripsi,
                                        fotoUrl = photoUrl,
                                        latitude = lat,
                                        longitude = lng,
                                        status = LaporanStatus.SELESAI // Auto selesai untuk laporan kegiatan petugas
                                    )

                                    SupabaseService.submitLaporan(newLaporan)
                                    isLoading = false
                                    Toast.makeText(context, "Laporan kegiatan berhasil dikirim!", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Gagal melacak GPS.", Toast.LENGTH_SHORT).show()
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
                    Text("Kirim Laporan Kerja", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


// ------------------- TAB 4: CHAT DENGAN WARGA / REALTIME -------------------
@Composable
fun PetugasChatTab(profile: Profile) {
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

        // Auto Scroll to Bottom on load
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
                        Text(activeContact!!.alamat ?: "Warga", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            // Message list
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

            // Input area
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
        // Inbox contacts list (Active resident listings in the zone)
        val residents = allProfiles.filter { it.role == UserRole.WARGA }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Kotak Masuk Chat Warga",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(residents) { resident ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { activeContact = resident },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "User Avatar",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(resident.nama, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(resident.noHp ?: "Tidak ada telepon", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
