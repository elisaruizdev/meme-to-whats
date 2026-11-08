package com.memetowhats.app.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memetowhats.app.media.Gallery
import com.memetowhats.app.ui.stickers.rememberStickerSender
import com.memetowhats.app.util.ShareTarget
import com.memetowhats.app.util.ShareTargets
import com.memetowhats.app.util.Sharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fila «Enviar a» con las apps de mensajería instaladas (un toque = se abre esa app con el
 * archivo listo) y un botón «Más» con el menú de compartir de Android.
 *
 * Si es un sticker ([isSticker]), WhatsApp va por el pack de stickers: es la única
 * forma de que allí llegue como sticker y no como foto.
 *
 * Al enviar, si todavía no se había guardado en la galería, se guarda solo (una vez).
 * [key] es el nombre del archivo en la biblioteca.
 */
@Composable
fun SendBar(
    file: File,
    mimeType: String,
    key: String,
    isSticker: Boolean,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val sendAsSticker = rememberStickerSender(snackbar)
    val scope = rememberCoroutineScope()
    // Lo enviado sin guardar antes se guarda en la galería, para no perderlo.
    val saveIfNeeded = {
        scope.launch {
            val savedNow = withContext(Dispatchers.IO) {
                runCatching { Gallery.saveOnce(context, file, mimeType, key) }.getOrDefault(false)
            }
            if (savedNow) snackbar.showSnackbar("También lo he guardado en tu galería")
        }
    }
    val targets by produceState(emptyList<ShareTarget>(), mimeType) {
        value = withContext(Dispatchers.IO) { ShareTargets.load(context, mimeType) }
    }

    Column(modifier) {
        Text(
            "Enviar a",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(contentPadding).padding(bottom = 8.dp),
        )
        LazyRow(contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(targets, key = { it.packageName }) { target ->
                val asSticker = isSticker && target.isWhatsApp
                TargetButton(
                    label = target.label,
                    hint = if (asSticker) "como sticker" else null,
                    onClick = {
                        saveIfNeeded()
                        if (asSticker) sendAsSticker(file, key) else Sharing.toApp(context, file, mimeType, target.packageName)
                    },
                ) {
                    Image(target.icon, contentDescription = null, modifier = Modifier.size(52.dp).clip(CircleShape))
                }
            }
            item(key = "more") {
                TargetButton(label = "Más", hint = null, onClick = {
                    saveIfNeeded()
                    Sharing.withChooser(context, file, mimeType)
                }) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetButton(label: String, hint: String?, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}
