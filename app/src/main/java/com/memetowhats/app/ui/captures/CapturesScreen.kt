package com.memetowhats.app.ui.captures

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.memetowhats.app.media.ScreenCapture
import com.memetowhats.app.util.MediaAccess
import com.memetowhats.app.util.mediaAccess
import com.memetowhats.app.util.mediaPermissions

/** «Convierte una captura»: tus capturas y grabaciones de pantalla, para hacer stickers y GIF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturesScreen(
    onBack: () -> Unit,
    onOpenCapture: (Uri, Boolean) -> Unit,
    onUseClipboardImage: (image: Uri, original: Uri?) -> Unit,
    onOpenTutorial: () -> Unit,
    viewModel: CapturesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val captures by viewModel.captures.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(mediaAccess(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = mediaAccess(context)
        viewModel.refresh()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            onOpenCapture(uri, isVideo)
        }
    }

    // Al volver (por ejemplo, después de hacer una captura) se recarga la lista.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = mediaAccess(context)
        if (access != MediaAccess.NONE) {
            viewModel.refresh()
            viewModel.startWatching()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convierte una captura") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTutorial) {
                        Icon(Icons.Default.Info, contentDescription = "Cómo hacer una captura")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Otra imagen o vídeo") },
            )
        },
    ) { padding ->
        val list = captures
        val gridState = rememberLazyGridState()
        // Si aparece una captura nueva, subimos arriba para que se vea la primera
        // (si no, la lista se queda fija en la que estabas viendo y la nueva queda oculta encima).
        val newest = list?.firstOrNull()?.uri
        LaunchedEffect(newest) {
            if (newest != null) gridState.animateScrollToItem(0)
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        "Captura → sticker · Grabación de pantalla → GIF",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    ClipboardBanner(onUse = onUseClipboardImage, modifier = Modifier.padding(bottom = 8.dp))
                    when (access) {
                        MediaAccess.NONE -> PermissionCard { permissionLauncher.launch(mediaPermissions()) }
                        MediaAccess.PARTIAL -> PartialAccessBanner { permissionLauncher.launch(mediaPermissions()) }
                        MediaAccess.FULL -> Unit
                    }
                }
            }
            when {
                access == MediaAccess.NONE || list == null -> Unit
                list.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Todavía no hay capturas.\nHaz una y aparecerá aquí al momento.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
                else -> items(list, key = { it.uri.toString() }) { capture ->
                    CaptureTile(capture, isNewest = capture == list.first()) { onOpenCapture(capture.uri, capture.isVideo) }
                }
            }
        }
    }
}

@Composable
private fun CaptureTile(capture: ScreenCapture, isNewest: Boolean, onOpen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (isNewest) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = capture.uri,
            contentDescription = if (capture.isVideo) "Grabación de pantalla" else "Captura de pantalla",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            if (isNewest) "Nueva" else DateUtils.getRelativeTimeSpanString(capture.dateAdded * 1000).toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isNewest) FontWeight.Bold else null,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    if (isNewest) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (capture.isVideo) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(DateUtils.formatElapsedTime(capture.durationMs / 1000), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Dame acceso a tus fotos para enseñarte aquí tus capturas de pantalla. Solo muestro las capturas, " +
                    "no el resto del álbum, y nada sale del móvil.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRequest) { Text("Dar permiso") }
        }
    }
}

@Composable
private fun PartialAccessBanner(onRequest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Solo ves las fotos que elegiste. Las capturas nuevas no saldrán.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRequest) { Text("Permitir todas") }
    }
}
