package com.memetowhats.app.ui.captures

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memetowhats.app.media.Originals
import com.memetowhats.app.util.AppPrefs
import kotlinx.coroutines.launch

/**
 * Si hay una imagen copiada (por ejemplo, con «Copiar» al hacer una captura), ofrece
 * convertirla en sticker. Solo se mira el *tipo* de lo copiado; el contenido no se lee
 * hasta que se pulsa «Crear sticker».
 *
 * [onUse] recibe la imagen y, si se encuentra, la captura original en la galería.
 */
@Composable
fun ClipboardBanner(onUse: (image: Uri, original: Uri?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Android solo deja mirar el portapapeles cuando la app tiene el foco.
    val focused = LocalWindowInfo.current.isWindowFocused
    var clipTime by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(focused) {
        if (focused) clipTime = pendingImageClip(context)
    }

    val time = clipTime ?: return
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("📋 Tienes una imagen copiada", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "¿La convertimos en sticker?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            TextButton(onClick = {
                AppPrefs.setLastClipUsed(context, time)
                clipTime = null
            }) { Text("Ahora no") }
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            scope.launch {
                val uri = clipboard(context).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                AppPrefs.setLastClipUsed(context, time)
                clipTime = null
                if (uri != null) {
                    val original = if (Originals.isDeletable(uri)) uri else Originals.matchScreenshot(context, time)
                    onUse(uri, original)
                }
            }
        }) { Text("Crear sticker") }
    }
}

private fun clipboard(context: Context) = context.getSystemService(ClipboardManager::class.java)

/** Hora de la imagen copiada si hay una que no se ha usado ni descartado; si no, null. */
private fun pendingImageClip(context: Context): Long? {
    val description = runCatching { clipboard(context).primaryClipDescription }.getOrNull() ?: return null
    if (!description.hasMimeType("image/*")) return null
    val time = description.timestamp
    return time.takeIf { it != AppPrefs.lastClipUsed(context) }
}
