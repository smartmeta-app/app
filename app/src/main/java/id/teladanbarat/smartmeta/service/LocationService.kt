package id.teladanbarat.smartmeta.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import id.teladanbarat.smartmeta.data.SupabaseService
import id.teladanbarat.smartmeta.data.UserRole
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocationService : Service() {

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "LocationService"
        private const val CHANNEL_ID = "SmartMetaLocationChannel"
        private const val NOTIFICATION_ID = 1011

        var isRunning = false
            private set

        // Status pengiriman lokasi terakhir, supaya bisa ditampilkan di UI
        // dashboard petugas — tanpa ini, satu-satunya cara cek apakah lokasi
        // benar-benar terkirim adalah lihat logcat lewat komputer, yang tidak
        // praktis kalau developer/petugas cuma pegang HP.
        private val _lastUpdateStatus = MutableStateFlow("Belum ada update lokasi terkirim")
        val lastUpdateStatus: StateFlow<String> = _lastUpdateStatus.asStateFlow()

        private fun timeNow() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        fun startService(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForegroundWithNotification()
        setupLocationTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Location Tracking Foreground Service started.")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification = createNotification("SMART META sedang melacak lokasi Anda")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pelacakan Lokasi Petugas")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Meta Tracking Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel pelacakan lokasi realtime SMART META"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20000) // 20 seconds
            .setMinUpdateIntervalMillis(15000) // 15 seconds fastest
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    onLocationUpdated(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Ambil satu fix GPS SEGERA saat service baru menyala, supaya
                // lokasi pertama langsung terkirim — tidak perlu menunggu
                // sampai 15-20 detik update periodik pertama (yang kadang
                // butuh lebih lama lagi kalau sinyal GPS lemah).
                val currentLocationRequest = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .build()
                fusedLocationClient?.getCurrentLocation(currentLocationRequest, CancellationTokenSource().token)
                    ?.addOnSuccessListener { location ->
                        if (location != null) {
                            onLocationUpdated(location)
                        } else {
                            _lastUpdateStatus.value = "GPS belum dapat titik awal (${timeNow()}). Pastikan lokasi HP aktif."
                            Log.e(TAG, "getCurrentLocation() null — GPS/network location kemungkinan mati.")
                        }
                    }
                    ?.addOnFailureListener { e ->
                        _lastUpdateStatus.value = "Gagal ambil GPS awal: ${e.message}"
                        Log.e(TAG, "getCurrentLocation() gagal", e)
                    }

                fusedLocationClient?.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.getMainLooper()
                )
            } catch (unlikely: SecurityException) {
                Log.e(TAG, "Lost location permissions. Could not request updates. $unlikely")
                _lastUpdateStatus.value = "Izin lokasi dicabut, tracking berhenti."
            }
        } else {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted.")
            _lastUpdateStatus.value = "Izin lokasi (ACCESS_FINE_LOCATION) belum diberikan."
        }
    }

    private fun onLocationUpdated(location: Location) {
        val currentProfile = SupabaseService.currentProfile.value
        if (currentProfile != null && currentProfile.role == UserRole.PETUGAS) {
            val petugasId = currentProfile.id
            val lat = location.latitude
            val lng = location.longitude
            Log.i(TAG, "Location updated: Lat $lat, Lng $lng for Petugas $petugasId")

            serviceScope.launch {
                try {
                    SupabaseService.updateLiveLocation(petugasId, lat, lng)
                    _lastUpdateStatus.value = "Terkirim ${timeNow()} (${"%.5f".format(lat)}, ${"%.5f".format(lng)})"
                } catch (e: Exception) {
                    // Jangan crash service — cukup log, update lokasi berikutnya akan
                    // dicoba lagi otomatis 15-30 detik kemudian.
                    Log.e(TAG, "Gagal mengirim update lokasi ke Supabase", e)
                    _lastUpdateStatus.value = "Gagal kirim ke server (${timeNow()}): ${e.message}"
                }
            }
        } else {
            Log.i(TAG, "No logged in petugas or user is citizen. Skipping update.")
            _lastUpdateStatus.value = "Tidak ada sesi petugas aktif — coba logout & login ulang."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        locationCallback?.let { fusedLocationClient?.removeLocationUpdates(it) }
        serviceJob.cancel()
        Log.i(TAG, "Location Tracking Foreground Service stopped.")
    }
}
