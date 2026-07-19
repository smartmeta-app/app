package id.teladanbarat.smartmeta.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Gerbang izin yang WAJIB dilewati sebelum LoginScreen muncul. Aplikasi ini
 * fundamental butuh kamera (selfie absensi, foto laporan) dan lokasi (peta,
 * tracking, validasi radius zona) — daripada minta izin nyicil di
 * tengah-tengah pemakaian (yang sering di-skip/ditolak user tanpa sadar
 * konsekuensinya), lebih jelas kalau diminta sekali di depan dengan
 * penjelasan, sebelum akun bisa dipakai sama sekali.
 */
@Composable
fun PermissionGateScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current

    fun hasPermission(perm: String) =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    var locationGranted by remember {
        mutableStateOf(hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
    }
    var cameraGranted by remember {
        mutableStateOf(hasPermission(Manifest.permission.CAMERA))
    }
    var deniedOnce by remember { mutableStateOf(false) }

    // Kalau semua sudah diizinkan (mis. sebelumnya sudah pernah kasih izin
    // di sesi lalu), langsung lanjut tanpa perlu tampilkan layar ini.
    LaunchedEffect(locationGranted, cameraGranted) {
        if (locationGranted && cameraGranted) {
            onAllGranted()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        cameraGranted = results[Manifest.permission.CAMERA] == true ||
            hasPermission(Manifest.permission.CAMERA)
        if (!locationGranted || !cameraGranted) deniedOnce = true
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Izin Diperlukan",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SMART META butuh dua izin ini supaya fitur inti aplikasi (pelacakan, absensi, dan laporan foto) bisa berjalan. Izinkan dulu sebelum masuk ke akun Anda.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            PermissionRow(
                icon = Icons.Default.LocationOn,
                title = "Lokasi",
                description = "Untuk pelacakan petugas, peta, dan validasi radius absensi.",
                granted = locationGranted
            )
            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Default.CameraAlt,
                title = "Kamera",
                description = "Untuk foto selfie absensi dan foto laporan lapangan.",
                granted = cameraGranted
            )

            if (deniedOnce && (!locationGranted || !cameraGranted)) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Izin ditolak. Aplikasi tidak bisa dipakai tanpa izin ini — coba lagi, atau aktifkan manual lewat Pengaturan HP > Aplikasi > SMART META > Izin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA)
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    if (deniedOnce) "Coba Lagi" else "Izinkan & Lanjutkan",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Text(
            text = if (granted) "✓" else "○",
            color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
