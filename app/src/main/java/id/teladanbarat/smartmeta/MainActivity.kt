package id.teladanbarat.smartmeta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import id.teladanbarat.smartmeta.data.Profile
import id.teladanbarat.smartmeta.data.SupabaseService
import id.teladanbarat.smartmeta.data.UserRole
import id.teladanbarat.smartmeta.service.LocationService
import id.teladanbarat.smartmeta.ui.screens.LoginScreen
import id.teladanbarat.smartmeta.ui.screens.PetugasScreen
import id.teladanbarat.smartmeta.ui.screens.WargaScreen
import id.teladanbarat.smartmeta.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val MINIMIZED_NOTIFICATION_ID = 1012

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dismissMinimizedNotification()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            showMinimizedNotification()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SmartMetaLocationChannel",
                "Smart Meta Tracking Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel pelacakan lokasi realtime SMART META"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showMinimizedNotification() {
        val currentProfile = SupabaseService.currentProfile.value ?: return
        if (currentProfile.role != UserRole.PETUGAS) return

        createNotificationChannel()

        val isTracking = LocationService.isRunning
        val title = "Aplikasi SMART META di Latar Belakang"
        val content = if (isTracking) {
            "Pelacakan Lokasi: AKTIF. Anda sedang dilacak saat ini."
        } else {
            "Pelacakan Lokasi: NONAKTIF. Anda tidak sedang dilacak saat ini."
        }

        val notification = NotificationCompat.Builder(this, "SmartMetaLocationChannel")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(false)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MINIMIZED_NOTIFICATION_ID, notification)
    }

    private fun dismissMinimizedNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MINIMIZED_NOTIFICATION_ID)
    }
}

@Composable
fun MainAppContent() {
    val currentProfile by SupabaseService.currentProfile.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (currentProfile == null) {
        LoginScreen(
            onLoginSuccess = { profile ->
                // Handled internally by SupabaseService.login updating the state flow
            }
        )
    } else {
        when (currentProfile!!.role) {
            UserRole.PETUGAS -> {
                PetugasScreen(
                    profile = currentProfile!!,
                    onLogout = {
                        coroutineScope.launch {
                            SupabaseService.logout()
                        }
                    }
                )
            }
            UserRole.WARGA -> {
                WargaScreen(
                    profile = currentProfile!!,
                    onLogout = {
                        coroutineScope.launch {
                            SupabaseService.logout()
                        }
                    }
                )
            }
            else -> {
                // Admin or unrecognized role, fallback
                WargaScreen(
                    profile = currentProfile!!,
                    onLogout = {
                        coroutineScope.launch {
                            SupabaseService.logout()
                        }
                    }
                )
            }
        }
    }
}
