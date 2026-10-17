package com.memetowhats.app.ui.detail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.memetowhats.app.data.Category
import com.memetowhats.app.data.LibraryItem
import com.memetowhats.app.data.MediaType
import com.memetowhats.app.util.Sharing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel(factory = DetailViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item
    val file = state.file
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (item.favorite) "Quitar de favoritos" else "Añadir a favoritos",
                                tint = if (item.favorite) Color(0xFFE53935) else Color.Unspecified,
                            )
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (item != null && file != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { Sharing.toWhatsApp(context, file, item.mimeType) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Text("WhatsApp", Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(
                            onClick = { Sharing.withChooser(context, file, item.mimeType) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Text("Otras apps", Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (item == null || file == null) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Preview(item, file)
            DetailsForm(
                item = item,
                categories = state.categories,
                onSave = viewModel::save,
                onAddCategory = viewModel::addCategory,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar de la biblioteca?") },
            text = { Text("Se borra la copia guardada en la app. El original de tu galería no se toca.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Preview(item: LibraryItem, file: File) {
    val modifier = Modifier
        .fillMaxWidth()
        .background(Color.Black)
    if (item.type == MediaType.VIDEO) {
        VideoPlayer(file, modifier.height(320.dp))
    } else {
        AsyncImage(
            model = file,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = modifier.heightIn(max = 420.dp),
        )
    }
}

@Composable
private fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        // Pausamos al salir de la app para que no siga sonando de fondo.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsForm(
    item: LibraryItem,
    categories: List<Category>,
    onSave: (title: String, audioText: String, tags: String, categoryId: Long?) -> Unit,
    onAddCategory: (name: String, onCreated: (Long) -> Unit) -> Unit,
) {
    var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
    var audioText by rememberSaveable(item.id) { mutableStateOf(item.audioText) }
    var tags by rememberSaveable(item.id) { mutableStateOf(item.tags) }
    var categoryId by rememberSaveable(item.id) { mutableStateOf(item.categoryId) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }

    val dirty = title != item.title || audioText != item.audioText || tags != item.tags || categoryId != item.categoryId

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = audioText,
            onValueChange = { audioText = it },
            label = { Text("Frase o sonido") },
            supportingText = { Text("Lo que se oye: una frase, una canción o un sonido. Sirve para buscarlo.") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Etiquetas") },
            supportingText = { Text("Separadas por comas: risa, fútbol, cumpleaños") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = categoryMenuOpen,
            onExpandedChange = { categoryMenuOpen = it },
        ) {
            OutlinedTextField(
                value = categories.firstOrNull { it.id == categoryId }?.name ?: "Sin categoría",
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoría") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Sin categoría") },
                    onClick = {
                        categoryId = null
                        categoryMenuOpen = false
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            categoryId = category.id
                            categoryMenuOpen = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Nueva categoría…") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        categoryMenuOpen = false
                        showNewCategory = true
                    },
                )
            }
        }

        Button(
            onClick = { onSave(title, audioText, tags, categoryId) },
            enabled = dirty,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (dirty) "Guardar cambios" else "Guardado")
        }
    }

    if (showNewCategory) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("Nueva categoría") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Ej.: Fútbol, Familia, Reacciones") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddCategory(name) { categoryId = it }
                        showNewCategory = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("Cancelar") } },
        )
    }
}
