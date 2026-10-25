package com.example.pathy

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.pathy.data.osm.TrailRepository
import com.example.pathy.i18n.AppLocale
import com.example.pathy.tracking.UploadScheduler
import com.mapbox.common.MapboxOptions

class App : Application() {

    /** Application-level strings (notifications, toasts from view models) follow the app language. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Android 13+: pick up a language the user chose in system settings.
        AppLocale.syncFromSystem(this)
        TrailRepository.init(this)
        guardMapToken()
        // Outings recorded offline (or before signing in) go up as soon as there is a connection.
        UploadScheduler.enqueue(this)
    }

    /**
     * A map screen without a Mapbox public token used to crash the app on opening. A build that was made
     * without MAPBOX_PUBLIC_TOKEN (debug builds only; release builds refuse to be made) now starts with
     * blank maps and says why, instead of crashing.
     */
    private fun guardMapToken() {
        if (getString(R.string.mapbox_access_token).startsWith("pk.")) return
        try {
            MapboxOptions.accessToken = "pk.missing-add-MAPBOX_PUBLIC_TOKEN-to-local.properties"
        } catch (e: LinkageError) {
            // The map's native library is not there (JVM unit tests); nothing is drawn in that case anyway.
            return
        }
        Log.w("PATHY", "No Mapbox public token: maps stay blank. Add MAPBOX_PUBLIC_TOKEN=pk... to local.properties.")
        if (BuildConfig.DEBUG) {
            Toast.makeText(this, "Maps are blank: add MAPBOX_PUBLIC_TOKEN (pk...) to local.properties", Toast.LENGTH_LONG).show()
        }
    }
}
