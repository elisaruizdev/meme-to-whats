package com.memetowhats.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memetowhats.app.media.Originals
import com.memetowhats.app.ui.captures.CapturesScreen
import com.memetowhats.app.ui.detail.DetailScreen
import com.memetowhats.app.ui.editor.EditorScreen
import com.memetowhats.app.ui.home.HomeScreen
import com.memetowhats.app.ui.library.LibraryScreen
import com.memetowhats.app.ui.onboarding.OnboardingScreen
import com.memetowhats.app.ui.stickers.StickersScreen
import com.memetowhats.app.ui.theme.MemeTheme
import com.memetowhats.app.util.AppPrefs
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Imagen o vídeo compartido con la app, pendiente de abrir en el editor. */
    private val sharedForEditor = mutableStateOf<Pair<Uri, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleSharedMedia(intent)

        setContent {
            MemeTheme {
                val nav = rememberNavController()
                val shared = sharedForEditor.value
                LaunchedEffect(shared) {
                    if (shared != null) {
                        nav.openEditor(shared.first, shared.second)
                        sharedForEditor.value = null
                    }
                }
                // La primera vez se abre el tutorial; si llega algo compartido, se va directo al editor.
                val start = remember { if (AppPrefs.onboardingDone(this) || shared != null) "home" else "onboarding" }
                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onFinish = { capture ->
                                AppPrefs.setOnboardingDone(this@MainActivity)
                                if (nav.previousBackStackEntry != null) {
                                    nav.popBackStack()
                                } else {
                                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                                }
                                if (capture != null) nav.openEditor(capture, isVideo = false)
                            },
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenCaptures = { nav.navigate("captures") },
                            onUseClipboardImage = { image, original -> nav.openEditor(image, isVideo = false, original = original) },
                            onOpenCreation = { id -> nav.navigate("item/$id") },
                            onOpenStickers = { nav.navigate("stickers") },
                            onOpenLibrary = { nav.navigate("library") },
                        )
                    }
                    composable("captures") {
                        CapturesScreen(
                            onBack = { nav.popBackStack() },
                            onOpenCapture = { uri, isVideo -> nav.openEditor(uri, isVideo) },
                            onUseClipboardImage = { image, original -> nav.openEditor(image, isVideo = false, original = original) },
                            onOpenTutorial = { nav.navigate("onboarding") },
                        )
                    }
                    composable(
                        route = "editor?uri={uri}&video={video}&original={original}",
                        arguments = listOf(
                            navArgument("uri") { type = NavType.StringType },
                            navArgument("video") { type = NavType.BoolType },
                            navArgument("original") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) {
                        EditorScreen(onBack = { nav.popBackStack() })
                    }
                    composable("stickers") {
                        StickersScreen(onBack = { nav.popBackStack() })
                    }
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

    /** [original] = la captura en la galería, para poder quitarla y quedarse solo con el sticker o GIF. */
    private fun NavHostController.openEditor(uri: Uri, isVideo: Boolean, original: Uri? = uri) {
        val originalArg = original?.takeIf { Originals.isDeletable(it) }?.let { "&original=${Uri.encode(it.toString())}" }.orEmpty()
        navigate("editor?uri=${Uri.encode(uri.toString())}&video=$isVideo$originalArg")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedMedia(intent)
    }

    /**
     * Lo que se comparte con la app desde la galería, WhatsApp, etc.:
     * un solo archivo se abre en el editor; varios se guardan en la biblioteca.
     */
    private fun handleSharedMedia(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return
                val type = intent.type ?: contentResolver.getType(uri).orEmpty()
                sharedForEditor.value = uri to type.startsWith("video/")
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                if (uris.isEmpty()) return
                val repository = (application as MemeApp).repository
                lifecycleScope.launch {
                    val imported = repository.import(uris)
                    val message = if (imported == 1) "1 archivo añadido a la biblioteca" else "$imported archivos añadidos a la biblioteca"
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
