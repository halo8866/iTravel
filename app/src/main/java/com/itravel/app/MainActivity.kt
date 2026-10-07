package com.itravel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.itravel.app.ui.ITravelTheme
import com.itravel.app.ui.TravelMapApp
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMdroid must be configured before any MapView is created.
        val ctx = applicationContext
        // 1) Put the tile cache inside the app-private cache dir so tiles can be written
        //    without needing WRITE_EXTERNAL_STORAGE (blocked by scoped storage).
        val tileCache = File(ctx.cacheDir, "osmdroid").apply { if (!exists()) mkdirs() }

        @Suppress("DEPRECATION")
        Configuration.getInstance().apply {
            load(ctx, android.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
            userAgentValue = "iTravel/1.0 (travel-journal app)"
            setOsmdroidTileCache(tileCache)
        }

        setContent {
            ITravelTheme {
                TravelMapApp(viewModel)
            }
        }
    }
}

