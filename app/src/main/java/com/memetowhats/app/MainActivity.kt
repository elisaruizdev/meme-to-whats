package com.memetowhats.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memetowhats.app.ui.detail.DetailScreen
import com.memetowhats.app.ui.library.LibraryScreen
import com.memetowhats.app.ui.theme.MemeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleSharedMedia(intent)

        setContent {
            MemeTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(onOpenItem = { id -> nav.navigate("item/$id") })
                    }
                    composable(
                        route = "item/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) {
                        DetailScreen(onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedMedia(intent)
    }

    /** Importa lo que se comparte con la app desde WhatsApp, la galería, etc. */
    private fun handleSharedMedia(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isEmpty()) return

        val repository = (application as MemeApp).repository
        lifecycleScope.launch {
            val imported = repository.import(uris)
            val message = if (imported == 1) "1 archivo añadido" else "$imported archivos añadidos"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
