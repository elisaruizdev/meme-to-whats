package com.memetowhats.app.ui.stickers

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memetowhats.app.stickers.StickerStore
import com.memetowhats.app.stickers.WhatsAppStickers
import com.memetowhats.app.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Enviar un sticker por WhatsApp *como sticker*.
 *
 * WhatsApp no deja que otra app meta un sticker directamente en un chat: solo usa stickers
 * de packs añadidos. Así que:
 * 1. El sticker se mete en nuestro pack «Meme to Whats».
 * 2. Si el pack no está en WhatsApp, se abre el diálogo de WhatsApp para añadirlo (solo la primera vez).
 * 3. Se explica dónde encontrarlo en el chat y se abre WhatsApp.
 *
 * Devuelve la función `send(archivoWebp, clave)`; [key] es el nombre del sticker en la biblioteca.
 */
@Composable
fun rememberStickerSender(snackbar: SnackbarHostState): (file: File, key: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHowTo by remember { mutableStateOf(false) }
    var pendingPackId by remember { mutableStateOf<String?>(null) }

    val addPackLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val error = result.data?.getStringExtra("validation_error")
        when {
            result.resultCode == Activity.RESULT_OK -> {
                pendingPackId?.let { AppPrefs.setPackAddedToWhatsApp(context, it) }
                showHowTo = true
            }
            error != null -> scope.launch { snackbar.showSnackbar("WhatsApp no aceptó el pack: $error") }
        }
    }

    if (showHowTo) {
        HowToUseDialog(
            onOpenWhatsApp = {
                showHowTo = false
                WhatsAppStickers.openIntent(context)?.let { runCatching { context.startActivity(it) } }
            },
            onDismiss = { showHowTo = false },
        )
    }

    return remember(snackbar) {
        { file, key ->
            scope.launch {
                val pack = withContext(Dispatchers.IO) { StickerStore.add(context, file, key) }
                val added = AppPrefs.packAddedToWhatsApp(context, pack.id) ||
                    withContext(Dispatchers.IO) { WhatsAppStickers.isPackAdded(context, pack.id) }
                if (added) AppPrefs.setPackAddedToWhatsApp(context, pack.id)
                pendingPackId = pack.id
                val intent = WhatsAppStickers.addPackIntent(context, pack)
                when {
                    intent == null -> snackbar.showSnackbar("WhatsApp no está instalado")
                    added -> showHowTo = true
                    else -> runCatching { addPackLauncher.launch(intent) }
                        .onFailure { snackbar.showSnackbar("Tu WhatsApp no admite añadir packs de stickers") }
                }
            }
        }
    }
}

@Composable
private fun HowToUseDialog(onOpenWhatsApp: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tu sticker ya está en WhatsApp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Está en el pack «${StickerStore.PUBLISHER}». Para mandarlo:")
                HowToStep(1, "Abre el chat al que quieres enviarlo.")
                HowToStep(2, "Toca el icono de la carita 🙂 junto a la caja de texto.")
                HowToStep(3, "Ve a la pestaña de stickers y busca el pack «${StickerStore.PUBLISHER}».")
                Text(
                    "Tu sticker más reciente es el primero del pack. Si acabas de crearlo y no aparece, " +
                        "cierra y vuelve a abrir el panel de stickers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Truco: para tener el pack el primero de todos, en el panel de stickers toca «+» › " +
                        "«Mis stickers» y arrastra «${StickerStore.PUBLISHER}» arriba con el icono ≡. Solo hay que hacerlo una vez.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenWhatsApp) { Text("Abrir WhatsApp") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun HowToStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
