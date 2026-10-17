package com.memetowhats.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.memetowhats.app.data.Category
import com.memetowhats.app.data.MediaType
import com.memetowhats.app.util.Sharing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenItem: (Long) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showCategories by rememberSaveable { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Meme to Whats") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Añadir") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("Busca por título, frase, sonido o etiqueta") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            FilterRow(
                state = state,
                onToggleFavorites = viewModel::toggleFavoritesOnly,
                onType = viewModel::setType,
                onCategory = viewModel::selectCategory,
                onManageCategories = { showCategories = true },
            )

            when {
                state.loading -> Unit
                state.totalCount == 0 -> EmptyMessage(
                    "Tu biblioteca está vacía.\n\nPulsa «Añadir» para elegir memes y vídeos, " +
                        "o compártelos con Meme to Whats desde WhatsApp o la galería.",
                )
                state.entries.isEmpty() -> EmptyMessage("No hay nada que coincida con la búsqueda.")
                else -> MediaGrid(
                    entries = state.entries,
                    onOpen = { onOpenItem(it.item.id) },
                    onQuickShare = { Sharing.toWhatsApp(context, it.file, it.item.mimeType) },
                )
            }
        }
    }

    if (showCategories) {
        CategoriesDialog(
            categories = state.categories,
            onAdd = viewModel::addCategory,
            onDelete = viewModel::deleteCategory,
            onDismiss = { showCategories = false },
        )
    }
}

@Composable
private fun FilterRow(
    state: LibraryState,
    onToggleFavorites: () -> Unit,
    onType: (TypeFilter) -> Unit,
    onCategory: (Long) -> Unit,
    onManageCategories: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filters.favoritesOnly,
                onClick = onToggleFavorites,
                label = { Text("Favoritos") },
                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
        item {
            FilterChip(
                selected = state.filters.type == TypeFilter.VIDEOS,
                onClick = { onType(TypeFilter.VIDEOS) },
                label = { Text("Vídeos") },
            )
        }
        item {
            FilterChip(
                selected = state.filters.type == TypeFilter.IMAGES,
                onClick = { onType(TypeFilter.IMAGES) },
                label = { Text("Imágenes") },
            )
        }
        items(state.categories, key = { it.id }) { category ->
            FilterChip(
                selected = state.filters.categoryId == category.id,
                onClick = { onCategory(category.id) },
                label = { Text(category.name) },
            )
        }
        item {
            AssistChip(
                onClick = onManageCategories,
                label = { Text("Categorías") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    entries: List<LibraryEntry>,
    onOpen: (LibraryEntry) -> Unit,
    onQuickShare: (LibraryEntry) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.item.id }) { entry ->
            Column(
                Modifier.combinedClickable(
                    onClick = { onOpen(entry) },
                    onLongClick = {
                        // Mantener pulsado = enviar directo a WhatsApp.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onQuickShare(entry)
                    },
                ),
            ) {
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = entry.file,
                        contentDescription = entry.item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (entry.item.type == MediaType.VIDEO) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Vídeo",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .padding(4.dp),
                        )
                    }
                    if (entry.item.favorite) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Favorito",
                            tint = Color(0xFFE53935),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.item.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoriesDialog(
    categories: List<Category>,
    onAdd: (String) -> Unit,
    onDelete: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categorías") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Nueva categoría") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onAdd(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Crear categoría")
                    }
                }
                if (categories.isEmpty()) {
                    Text(
                        "Todavía no hay categorías.",
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn {
                    items(categories, key = { it.id }) { category ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { pendingDelete = category }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar ${category.name}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Borrar «${category.name}»?") },
            text = { Text("Los memes de esta categoría no se borran; solo se quedan sin categoría.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(category)
                    pendingDelete = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}
