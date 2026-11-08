package com.memetowhats.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memetowhats.app.media.Transcriber
import java.util.Locale

/**
 * Subtítulos del GIF: generarlos a partir del sonido del tramo elegido y corregirlos
 * frase a frase antes de crear el GIF.
 */
@Composable
fun SubtitlesSection(state: EditorState, viewModel: EditorViewModel) {
    if (!state.hasAudio) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtítulos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        val download = state.modelDownload
        when {
            download != null -> {
                Text("Descargando el reconocimiento de voz… ${(download * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { download }, modifier = Modifier.fillMaxWidth())
            }
            state.transcribing -> {
                Text("Escuchando el audio…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.subtitles.isEmpty() -> {
                OutlinedButton(onClick = viewModel::generateSubtitles, modifier = Modifier.fillMaxWidth()) {
                    Text("🎙️  Generar subtítulos del audio")
                }
                Text(
                    "Escucho lo que se dice en el tramo elegido y lo escribo abajo del GIF. Podrás corregirlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                if (state.subtitlesRange != null && state.subtitlesRange != (state.startMs to state.endMs)) {
                    Text(
                        "Has cambiado el tramo: vuelve a generarlos para que coincidan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.subtitles.forEachIndexed { index, subtitle ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            seconds(subtitle.startMs - state.startMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp),
                        )
                        OutlinedTextField(
                            value = subtitle.text,
                            onValueChange = { viewModel.editSubtitle(index, it) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeSubtitle(index) }) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar esta frase")
                        }
                    }
                }
                Row {
                    TextButton(onClick = viewModel::generateSubtitles) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Volver a generar")
                    }
                    TextButton(onClick = viewModel::clearSubtitles) { Text("Quitar subtítulos") }
                }
            }
        }
    }

    if (state.askModelDownload) {
        AlertDialog(
            onDismissRequest = viewModel::dismissModelDownload,
            title = { Text("Reconocimiento de voz") },
            text = {
                Text(
                    "Para escribir lo que se oye hace falta descargar el reconocimiento de voz en español " +
                        "(unos ${Transcriber.MODEL_SIZE_MB} MB, solo una vez; mejor con Wi-Fi).\n\n" +
                        "Después funciona sin internet y el audio nunca sale de tu móvil.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmModelDownload) { Text("Descargar") } },
            dismissButton = { TextButton(onClick = viewModel::dismissModelDownload) { Text("Ahora no") } },
        )
    }
}

private fun seconds(ms: Long) = String.format(Locale.getDefault(), "%.1f s", ms.coerceAtLeast(0) / 1000f)
