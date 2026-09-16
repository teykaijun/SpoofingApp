package com.spoofingmobileapp.spoof

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.spoofingmobileapp.MainActivity
import com.spoofingmobileapp.R
import com.spoofingmobileapp.geo.LocationSample
import com.spoofingmobileapp.geo.RouteSimulator
import com.spoofingmobileapp.geo.Units
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that keeps pushing mock fixes while spoofing is active. Android discards
 * mock locations that stop updating, so fixes are re-sent every [SpoofConfig.updateIntervalMillis].
 */
class MockLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var pusher: MockLocationPusher
    private var session: Job? = null
    private var currentConfig: SpoofConfig? = null

    @Volatile
    private var traveledMeters = 0.0

    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        pusher = MockLocationPusher(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSpoofing()
            return START_NOT_STICKY
        }
        // startForegroundService() must always be answered with startForeground(), even for bad input.
        if (!enterForeground()) {
            stopSpoofing()
            return START_NOT_STICKY
        }
        val config = SpoofConfig.fromIntent(intent)
        if (config == null) {
            stopSpoofing()
        } else {
            startSpoofing(config)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        pusher.stop()
        releaseWakeLock()
        SpoofSession.update(SpoofState.Idle)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        return try {
            val notification = buildNotification(getString(R.string.notification_title_fixed), null)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: SecurityException) {
            SpoofSession.reportError(SpoofError.MissingLocationPermission)
            false
        } catch (e: IllegalStateException) {
            val blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            SpoofSession.reportError(
                if (blocked) SpoofError.ForegroundServiceBlocked else SpoofError.Unexpected(e.describe()),
            )
            false
        }
    }

    private fun startSpoofing(config: SpoofConfig) {
        val previous = currentConfig
        session?.cancel()
        try {
            pusher.start(config.mockPlayServices)
        } catch (e: SecurityException) {
            SpoofSession.reportError(SpoofError.NotMockLocationApp)
            stopSpoofing()
            return
        } catch (e: IllegalArgumentException) {
            SpoofSession.reportError(SpoofError.Unexpected(e.describe()))
            stopSpoofing()
            return
        }
        SpoofSession.clearError()
        acquireWakeLock()

        // Keep route progress when only speed, loop or accuracy changed.
        val resumeFrom = if (previous?.waypoints == config.waypoints) traveledMeters else 0.0
        currentConfig = config
        session = scope.launch { runSession(config, resumeFrom) }
    }

    private suspend fun runSession(config: SpoofConfig, resumeFrom: Double) {
        val speed = if (config.isRoute) config.speedMetersPerSecond else 0.0
        val simulator = RouteSimulator(config.waypoints, speed, config.loop)
        val total = simulator.totalDistanceMeters
        var traveled = resumeFrom
        var lastTick = SystemClock.elapsedRealtime()
        var lastWakeLockRefresh = lastTick
        var nextNotificationAt = 0L
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                traveled += speed * (now - lastTick) / 1000.0
                if (total > 0.0) {
                    traveled = if (config.loop) traveled % total else traveled.coerceAtMost(total)
                }
                lastTick = now
                traveledMeters = traveled

                val sample = simulator.sampleAtDistance(traveled)
                pusher.push(sample, config.accuracyMeters)
                SpoofSession.update(SpoofState.Active(config, sample))

                if (now >= nextNotificationAt) {
                    updateNotification(config, sample)
                    nextNotificationAt = if (config.isRoute) now + NOTIFICATION_REFRESH_MILLIS else Long.MAX_VALUE
                }
                if (now - lastWakeLockRefresh > WAKE_LOCK_REFRESH_MILLIS) {
                    wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MILLIS)
                    lastWakeLockRefresh = now
                }
                delay(config.updateIntervalMillis)
            }
        } catch (e: SecurityException) {
            // The user picked another mock location app while we were running.
            fail(SpoofError.NotMockLocationApp)
        } catch (e: IllegalArgumentException) {
            // Another app removed or replaced our test providers.
            fail(SpoofError.Unexpected(e.describe()))
        }
    }

    private suspend fun fail(error: SpoofError) {
        SpoofSession.reportError(error)
        withContext(Dispatchers.Main) { stopSpoofing() }
    }

    private fun stopSpoofing() {
        session?.cancel()
        session = null
        currentConfig = null
        traveledMeters = 0.0
        pusher.stop()
        releaseWakeLock()
        SpoofSession.update(SpoofState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(config: SpoofConfig, sample: LocationSample) {
        val notification = if (config.isRoute) {
            buildNotification(
                getString(R.string.notification_title_route),
                getString(
                    R.string.notification_text_route,
                    Units.formatDistance(sample.distanceMeters),
                    Units.formatDistance(sample.totalDistanceMeters),
                    Units.formatSpeed(config.speedMetersPerSecond),
                ),
            )
        } else {
            buildNotification(getString(R.string.notification_title_fixed), sample.position.format(5))
        }
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, text: String?): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocationSpoofer:session")
            .apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun Exception.describe(): String = message ?: javaClass.simpleName

    companion object {
        private const val CHANNEL_ID = "spoofing"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_START = "com.spoofingmobileapp.action.START"
        private const val ACTION_STOP = "com.spoofingmobileapp.action.STOP"
        private const val NOTIFICATION_REFRESH_MILLIS = 5_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 2 * 60 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MILLIS = 60 * 60 * 1000L

        /** Starts or updates spoofing. Call while the app is in the foreground. */
        fun start(context: Context, config: SpoofConfig) {
            val intent = config.writeTo(Intent(context, MockLocationService::class.java).setAction(ACTION_START))
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                SpoofSession.reportError(SpoofError.ForegroundServiceBlocked)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MockLocationService::class.java))
        }

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
