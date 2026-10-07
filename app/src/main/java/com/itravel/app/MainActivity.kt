package com.itravel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.itravel.app.ui.ITravelTheme
import com.itravel.app.ui.TimelineScreen
import com.itravel.app.ui.TravelMapApp
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMdroid must be configured before any MapView is created.
        val ctx = applicationContext
        val tileCache = File(ctx.cacheDir, "osmdroid").apply { if (!exists()) mkdirs() }

        @Suppress("DEPRECATION")
        Configuration.getInstance().apply {
            load(ctx, android.preference.PreferenceManager.getDefaultSharedPreferences(ctx))
            userAgentValue = "iTravel/1.0 (travel-journal app)"
            setOsmdroidTileCache(tileCache)
        }

        setContent {
            ITravelTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(vm: AppViewModel) {
    var showTimeline by remember { mutableStateOf(false) }
    val places by vm.places.collectAsState()

    if (showTimeline) {
        TimelineScreen(
            places = places,
            onBack = { showTimeline = false }
        )
    } else {
        TravelMapApp(vm, onOpenTimeline = { showTimeline = true })
    }
}

