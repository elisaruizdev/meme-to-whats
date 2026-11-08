package com.memetowhats.app.ui.home

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.memetowhats.app.media.ScreenCapture
import com.memetowhats.app.ui.captures.ClipboardBanner
import com.memetowhats.app.ui.share.SendBar
import com.memetowhats.app.util.MediaAccess
import com.memetowhats.app.util.mediaAccess
import com.memetowhats.app.util.mediaPermissions

/**
 * Inicio: «Mis creaciones» (los stickers y GIF que has hecho), con filtros Todo / Stickers / GIF.
 * Las capturas para convertir están en su propia pantalla (icono ⊕ de arriba).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCaptures: () -> Unit,
    onUseClipboardImage: (image: Uri, original: Uri?) -> Unit,
    onOpenCreation: (Long) -> Unit,
    onOpenStickers: () -> Unit,
    onOpenLibrary: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Creación elegida con pulsación larga: se abre una hoja para enviarla.
    var sending by remember { mutableStateOf<CreationEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meme to Whats") },
                actions = {
                    IconButton(onClick = onOpenCaptures) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Convertir una captura")
                    }
                    IconButton(onClick = onOpenStickers) {
                        Icon(Icons.Default.Face, contentDescription = "Packs de stickers para WhatsApp")
                    }
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Biblioteca")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val gridState = rememberLazyGridState()
        // Al volver tras crear algo, subimos arriba para que lo nuevo se vea el primero.
        val newest = state.visibleCreations.firstOrNull()?.item?.id
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
            fullLine {
                ClipboardBanner(onUse = onUseClipboardImage, modifier = Modifier.padding(bottom = 8.dp))
            }
            fullLine {
                Column {
                    Text("Mis creaciones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val stickers = state.creations.count { it.isSticker }
                        val gifs = state.creations.size - stickers
                        FilterChip(
                            selected = state.filter == CreationFilter.ALL,
                            onClick = { viewModel.setFilter(CreationFilter.ALL) },
                            label = { Text("Todo (${state.creations.size})") },
                        )
                        FilterChip(
                            selected = state.filter == CreationFilter.STICKERS,
                            onClick = { viewModel.setFilter(CreationFilter.STICKERS) },
                            label = { Text("Stickers ($stickers)") },
                        )
                        FilterChip(
                            selected = state.filter == CreationFilter.GIFS,
                            onClick = { viewModel.setFilter(CreationFilter.GIFS) },
                            label = { Text("GIF ($gifs)") },
                        )
                    }
                }
            }

            if (state.visibleCreations.isEmpty()) {
                fullLine {
                    Text(
                        if (state.creations.isEmpty()) {
                            "Aún no has creado nada.\nToca una captura de arriba para hacer tu primer sticker."
                        } else {
                            "No tienes de este tipo todavía."
                        },
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            } else {
                items(state.visibleCreations, key = { it.item.id }) { entry ->
                    CreationTile(
                        entry = entry,
                        onOpen = { onOpenCreation(entry.item.id) },
                        onQuickSend = { sending = entry },
                    )
                }
                fullLine {
                    Text(
                        "Mantén pulsado para enviarlo rápido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }

    sending?.let { entry ->
        ModalBottomSheet(onDismissRequest = { sending = null }) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = entry.file,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(entry.item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
            Spacer(Modifier.height(16.dp))
            SendBar(
                file = entry.file,
                mimeType = entry.item.mimeType,
                key = entry.item.fileName.substringBeforeLast('.'),
                isSticker = entry.isSticker,
                snackbar = snackbar,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
        }
    }
}

private fun LazyGridScope.fullLine(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreationTile(entry: CreationEntry, onOpen: () -> Unit, onQuickSend: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onQuickSend()
                },
            ),
    ) {
        AsyncImage(
            model = entry.file,
            contentDescription = entry.item.title,
            // Los stickers se ven enteros (tienen transparencia); los GIF llenan la casilla.
            contentScale = if (entry.isSticker) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (entry.isSticker) 6.dp else 0.dp),
        )
        if (!entry.isSticker) {
            Text(
                "GIF",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
