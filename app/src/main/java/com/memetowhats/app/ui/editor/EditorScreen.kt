package com.memetowhats.app.ui.editor

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.memetowhats.app.media.GifMaker
import com.memetowhats.app.ui.share.SendBar
import com.memetowhats.app.util.Sharing
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onBack: () -> Unit, viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Aviso de Android para quitar la captura original (papelera o borrado).
    val removeOriginalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onRemoveOriginalResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(event.text)
                is EditorEvent.ConfirmRemoveOriginal ->
                    removeOriginalLauncher.launch(IntentSenderRequest.Builder(event.request).build())
            }
        }
    }

    val title = when {
        state.result != null -> if (state.isVideo) "Tu GIF" else "Tu sticker"
        state.isVideo -> "Crear GIF"
        else -> "Crear sticker"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            val result = state.result
            when {
                state.loadError -> Text(
                    "No se pudo abrir el archivo.",
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center,
                )
                state.preview == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                result != null -> ResultContent(
                    state = state,
                    snackbar = snackbar,
                    onSave = viewModel::saveToGallery,
                    onEditAgain = viewModel::editAgain,
                )
                else -> EditContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun EditContent(state: EditorState, viewModel: EditorViewModel) {
    val preview = state.preview ?: return
    val image = remember(preview) { preview.asImageBitmap() }
    Column(Modifier.fillMaxSize()) {
        CropBox(
            image = image,
            crop = state.crop,
            onCropChange = viewModel::setCrop,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
        )
        Column(
            Modifier
                // Como mucho la mitad de la pantalla (con scroll), para que el recorte siempre se vea.
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Arrastra las esquinas para recortar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::resetCrop) { Text("Todo") }
            }

            if (state.isVideo && state.durationMs > 0) {
                Text(
                    "De ${seconds(state.startMs)} a ${seconds(state.endMs)} " +
                        "(${seconds(state.endMs - state.startMs)}, máx. ${GifMaker.MAX_DURATION_MS / 1000} s)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RangeSlider(
                    value = state.startMs.toFloat()..state.endMs.toFloat(),
                    onValueChange = { viewModel.setRange(it.start.toLong(), it.endInclusive.toLong()) },
                    onValueChangeFinished = viewModel::refreshPreviewFrame,
                    valueRange = 0f..state.durationMs.toFloat(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(240 to "Pequeño", 320 to "Mediano", 400 to "Grande").forEach { (width, label) ->
                        FilterChip(
                            selected = state.gifWidth == width,
                            onClick = { viewModel.setGifWidth(width) },
                            label = { Text(label) },
                        )
                    }
                }
                SubtitlesSection(state, viewModel)
            }

            OutlinedTextField(
                value = state.caption,
                onValueChange = viewModel::setCaption,
                // Con subtítulos, el texto fijo se pone arriba para no taparlos.
                label = { Text(if (state.subtitles.isNotEmpty()) "Texto fijo arriba (opcional)" else "Texto (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.canReplaceOriginal) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.isVideo) "Quedarme solo con el GIF" else "Quedarme solo con el sticker",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Se guarda en tu galería y la captura original se quita" +
                                if (Build.VERSION.SDK_INT >= 30) " (va a la papelera)." else ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.replaceOriginal, onCheckedChange = viewModel::setReplaceOriginal)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.working) {
                if (state.isVideo) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text("Creando GIF… ${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::cancel) { Text("Cancelar") }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            } else {
                Button(onClick = viewModel::create, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isVideo) "Crear GIF" else "Crear sticker")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ResultContent(
    state: EditorState,
    snackbar: SnackbarHostState,
    onSave: () -> Unit,
    onEditAgain: () -> Unit,
) {
    val result = state.result ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .aspectRatio(if (result.isSticker) 1f else 0.75f),
        ) {
            // Fondo de cuadros para que se note la transparencia del sticker.
            if (result.isSticker) Checkerboard()
            AsyncImage(model = result.file, contentDescription = title(result.isSticker), modifier = Modifier.fillMaxSize())
        }
        Text(
            "${result.file.length() / 1024} KB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SendBar(
            file = result.file,
            mimeType = result.mimeType,
            key = result.key,
            isSticker = result.isSticker,
            snackbar = snackbar,
            modifier = Modifier.fillMaxWidth(),
        )
        if (result.isSticker) {
            Text(
                "En WhatsApp los stickers van en el pack «Meme to Whats»: la primera vez te pedirá añadirlo " +
                    "y después tus stickers nuevos aparecen allí solos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Guardar en la galería")
        }
        Spacer(Modifier.height(8.dp))
        // Si la captura original ya se quitó, no hay de dónde volver a editar.
        if (!state.originalRemoved) {
            TextButton(onClick = onEditAgain) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Volver a editar")
            }
        }
    }
}

private fun title(isSticker: Boolean) = if (isSticker) "Sticker creado" else "GIF creado"

@Composable
private fun Checkerboard() {
    val light = Color(0xFFE0E0E0)
    val dark = Color(0xFFBDBDBD)
    Canvas(Modifier.fillMaxSize()) {
        val cell = 12.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (y in 0 until rows) for (x in 0 until cols) {
            drawRect(if ((x + y) % 2 == 0) light else dark, Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}

private fun seconds(ms: Long) = String.format(Locale.getDefault(), "%.1f s", ms / 1000f)
