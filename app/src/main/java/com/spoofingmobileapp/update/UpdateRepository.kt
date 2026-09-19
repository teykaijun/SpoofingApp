package com.spoofingmobileapp.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.spoofingmobileapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Finds, downloads and installs newer builds published on the project's GitHub releases. */
class UpdateRepository(private val context: Context) {

    /** @throws IOException when GitHub cannot be reached. */
    suspend fun findUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        parseLatestRelease(readText(LATEST_RELEASE_URL), BuildConfig.VERSION_NAME)
    }

    /** @throws IOException when the download fails or arrives empty. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val target = File(directory, "LocationSpoofer-${info.versionName}.apk")

        val connection = open(info.apkUrl)
        try {
            val total = if (info.sizeBytes > 0) info.sizeBytes else connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        if (target.length() == 0L) throw IOException("The downloaded file was empty")
        target
    }

    /**
     * Hands the APK to Android's package installer. Android decides whether to ask the user:
     * the confirmation screen can only be skipped once this app is the installer of record,
     * which it becomes after installing its first update.
     */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_ENTRY, 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(statusIntent(sessionId).intentSender)
        }
    }

    /** Android 8+ requires the user to allow this app to install apps, once. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun readText(url: String): String {
        val connection = open(url, accept = "application/vnd.github+json")
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String? = null): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("User-Agent", "LocationSpoofer/${BuildConfig.VERSION_NAME}")
            if (accept != null) setRequestProperty("Accept", accept)
        }
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            throw IOException("GitHub returned HTTP $code")
        }
        return connection
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/teykaijun/SpoofingApp/releases/latest"
        const val TIMEOUT_MILLIS = 20_000
        const val DOWNLOAD_BUFFER = 64 * 1024
        const val APK_ENTRY = "update.apk"
    }
}
