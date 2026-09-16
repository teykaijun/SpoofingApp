package com.spoofingmobileapp

import android.app.Application
import com.spoofingmobileapp.data.FavoritesRepository
import com.spoofingmobileapp.data.SettingsRepository
import com.spoofingmobileapp.spoof.MockLocationPusher
import com.spoofingmobileapp.spoof.MockLocationService
import org.osmdroid.config.Configuration
import java.io.File

class SpooferApplication : Application() {

    lateinit var favorites: FavoritesRepository
        private set

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        favorites = FavoritesRepository(this)
        settings = SettingsRepository(this)
        MockLocationService.createNotificationChannel(this)

        // A fresh process means no session is running, so clear anything a killed session left behind.
        MockLocationPusher(this).removeLeftovers()

        // OpenStreetMap's tile policy requires an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }
}
