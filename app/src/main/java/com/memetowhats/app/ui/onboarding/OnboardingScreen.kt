package com.memetowhats.app.ui.onboarding

import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.memetowhats.app.util.MediaAccess
import com.memetowhats.app.util.mediaAccess
import com.memetowhats.app.util.mediaPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

// Colores fijos del tutorial (no dependen del tema del móvil, para que siempre luzca igual).
private val Green = Color(0xFF25D366)
private val Teal = Color(0xFF128C7E)
private val Purple = Color(0xFF7C4DFF)
private val Pink = Color(0xFFFF4081)
private val Orange = Color(0xFFFF9100)
private val Yellow = Color(0xFFFFD740)
private val Ink = Color(0xFF1B1B1F)

private fun heroBrush(step: OnboardingStep) = Brush.linearGradient(
    when (step) {
        OnboardingStep.WELCOME -> listOf(Teal, Green)
        OnboardingStep.PERMISSION -> listOf(Purple, Pink)
        OnboardingStep.TAKE_SCREENSHOT -> listOf(Orange, Pink)
        OnboardingStep.DONE -> listOf(Green, Yellow)
    },
)

private fun stepAccent(step: OnboardingStep) = when (step) {
    OnboardingStep.WELCOME -> Teal
    OnboardingStep.PERMISSION -> Purple
    OnboardingStep.TAKE_SCREENSHOT -> Orange
    OnboardingStep.DONE -> Teal
}

/**
 * Tutorial de la primera vez: explica la app, pide permiso y guía para hacer la primera captura.
 * Se queda esperando hasta detectar la captura y entonces ofrece convertirla en sticker.
 *
 * Cada paso tiene arriba una ilustración animada sobre un degradado y abajo el texto y los botones.
 * [onFinish] recibe la captura si se quiere abrir directamente en el editor.
 */
@Composable
fun OnboardingScreen(onFinish: (capture: Uri?) -> Unit, viewModel: OnboardingViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.goTo(OnboardingStep.TAKE_SCREENSHOT)
    }
    // Plan B si no se pudo leer la captura: que la elija de la galería.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onFinish(uri)
    }
    val pickImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // Android 14+: el sistema nos avisa cuando se hace una captura con la app delante.
    if (Build.VERSION.SDK_INT >= 34 && state.step == OnboardingStep.TAKE_SCREENSHOT) {
        val activity = context as? Activity
        DisposableEffect(activity) {
            val callback = Activity.ScreenCaptureCallback { viewModel.onSystemScreenshotDetected() }
            activity?.registerScreenCaptureCallback(ContextCompat.getMainExecutor(context), callback)
            onDispose { activity?.unregisterScreenCaptureCallback(callback) }
        }
    }

    BackHandler(enabled = state.step != OnboardingStep.WELCOME) {
        val previous = when (state.step) {
            OnboardingStep.PERMISSION, OnboardingStep.TAKE_SCREENSHOT -> OnboardingStep.WELCOME
            else -> OnboardingStep.TAKE_SCREENSHOT
        }
        viewModel.goTo(previous)
    }

    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInHorizontally(tween(350)) { it / 6 }) togetherWith fadeOut(tween(200))
        },
        label = "paso",
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> StepScaffold(
                step = step,
                onSkip = { onFinish(null) },
                hero = { WelcomeHero() },
                title = "Tus capturas,\nen stickers y GIF",
                body = "Haz una captura, recórtala y mándala como sticker por WhatsApp, Telegram o la app que uses. " +
                    "Si grabas la pantalla, te sale un GIF.",
            ) {
                PrimaryButton("Empezar", step) {
                    val next = if (mediaAccess(context) == MediaAccess.FULL) OnboardingStep.TAKE_SCREENSHOT else OnboardingStep.PERMISSION
                    viewModel.goTo(next)
                }
            }

            OnboardingStep.PERMISSION -> StepScaffold(
                step = step,
                onSkip = { onFinish(null) },
                hero = { PermissionHero() },
                title = "Deja que vea tus capturas",
                body = if (Build.VERSION.SDK_INT >= 34) {
                    "Así te enseño la última en cuanto la hagas. Cuando Android pregunte, elige «Permitir todo». " +
                        "Las fotos no salen de tu móvil."
                } else {
                    "Así te enseño la última en cuanto la hagas. Las fotos no salen de tu móvil."
                },
            ) {
                PrimaryButton("Permitir acceso", step) { permissionLauncher.launch(mediaPermissions()) }
            }

            OnboardingStep.TAKE_SCREENSHOT -> {
                val brand = remember { brandTips() }
                StepScaffold(
                    step = step,
                    onSkip = { onFinish(null) },
                    hero = { PressButtonsHero(brand.powerOnTop) },
                    title = "¡Hazle una captura a esta pantalla!",
                    body = "Pulsa a la vez encendido y bajar volumen, y suelta enseguida.",
                    extra = {
                        WaitingChip()
                        brand.tip?.let { TipCard("💡 Truco para tu ${brand.name}", it, Orange) }
                        if (state.showHelp) {
                            TipCard(
                                "🤔 Si no te sale",
                                "• Pulsa los dos botones justo a la vez: si uno va antes, solo cambia el volumen o se apaga la pantalla.\n" +
                                    "• Desliza desde arriba y busca «Captura de pantalla» en los ajustes rápidos.\n" +
                                    "• O dile al asistente: «Ok Google, haz una captura de pantalla».\n" +
                                    "• También vale hacerla en otra app: al volver aquí, la detecto.",
                                Purple,
                            )
                        }
                    },
                ) {
                    OutlinedButton(onClick = viewModel::toggleHelp, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text(if (state.showHelp) "Ocultar ayuda" else "No me sale 🙈")
                    }
                    TextButton(onClick = pickImage, modifier = Modifier.fillMaxWidth()) {
                        Text("Ya la he hecho, elegirla de la galería")
                    }
                }
            }

            OnboardingStep.DONE -> StepScaffold(
                step = step,
                onSkip = null,
                hero = { DoneHero(state.capture) },
                title = "¡Ya la tienes! 🎉",
                body = if (state.capture != null) {
                    "Ahora conviértela en sticker y mándala a quien quieras."
                } else {
                    "He visto que has hecho la captura. Elígela de la galería para convertirla en sticker."
                },
                extra = {
                    TipCard(
                        "🎬 ¿Y para hacer un GIF?",
                        "Graba la pantalla: desliza dos veces desde arriba y toca «Grabar pantalla» " +
                            "(si no está, añádelo con el lápiz). La grabación saldrá al abrir la app.",
                        Teal,
                    )
                },
            ) {
                PrimaryButton(if (state.capture != null) "Convertirla en sticker" else "Elegir la captura", step) {
                    state.capture?.let(onFinish) ?: pickImage()
                }
                TextButton(onClick = { onFinish(null) }, modifier = Modifier.fillMaxWidth()) { Text("Ir al inicio") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Estructura común: ilustración arriba + hoja con texto y botones abajo
// ---------------------------------------------------------------------------------------------

@Composable
private fun StepScaffold(
    step: OnboardingStep,
    onSkip: (() -> Unit)?,
    hero: @Composable BoxScope.() -> Unit,
    title: String,
    body: String,
    extra: @Composable () -> Unit = {},
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.48f)
                .background(heroBrush(step)),
        ) {
            BackgroundBubbles()
            Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp, bottom = 28.dp), contentAlignment = Alignment.Center) {
                hero()
            }
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepDots(current = step.ordinal, total = OnboardingStep.entries.size, modifier = Modifier.weight(1f))
                if (onSkip != null) {
                    TextButton(onClick = onSkip) { Text("Saltar", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
        // Hoja inferior que se monta un poco sobre el degradado.
        Surface(
            Modifier.fillMaxWidth().weight(0.52f).offset(y = (-24).dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 24.dp)) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    extra()
                }
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { actions() }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, step: OnboardingStep, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = stepAccent(step), contentColor = Color.White),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i <= current) 1f else 0.4f)),
            )
        }
    }
}

@Composable
private fun TipCard(title: String, text: String, accent: Color) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Burbujas translúcidas que flotan despacio en el fondo del degradado. */
@Composable
private fun BackgroundBubbles() {
    val t by rememberInfiniteTransition(label = "burbujas").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        val bubbles = listOf(
            Triple(0.12f, 0.25f, 46f), Triple(0.85f, 0.18f, 70f), Triple(0.78f, 0.82f, 54f),
            Triple(0.18f, 0.78f, 34f), Triple(0.5f, 0.1f, 24f),
        )
        bubbles.forEachIndexed { i, (x, y, r) ->
            val dy = sin((t + i * 0.2f) * 2 * Math.PI).toFloat() * 14.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.12f), r.dp.toPx(), Offset(size.width * x, size.height * y + dy))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Paso 1: la captura se convierte en sticker y en GIF
// ---------------------------------------------------------------------------------------------

@Composable
private fun WelcomeHero() {
    val flash = remember { Animatable(0f) }
    val fly = remember { Animatable(0f) }
    val sticker = remember { Animatable(0f) }
    val gif = remember { Animatable(0f) }
    val sent = remember { Animatable(0f) }

    // Bucle: destello de captura → la captura vuela → aparece el sticker → el GIF → "enviado".
    LaunchedEffect(Unit) {
        while (true) {
            listOf(flash, fly, sticker, gif, sent).forEach { it.snapTo(0f) }
            delay(700)
            flash.animateTo(1f, tween(90))
            flash.animateTo(0f, tween(400))
            fly.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            sticker.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
            launch { gif.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
            delay(450)
            sent.animateTo(1f, spring(dampingRatio = 0.6f))
            delay(2200)
        }
    }

    Box(Modifier.size(width = 300.dp, height = 230.dp)) {
        // Móvil con un meme en pantalla
        Phone(Modifier.align(Alignment.CenterStart).padding(start = 12.dp), width = 112.dp, height = 210.dp) {
            MemeScreen()
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }
        // La captura sale volando hacia la derecha mientras encoge
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
                .graphicsLayer {
                    translationX = fly.value * 110.dp.toPx()
                    translationY = -fly.value * 20.dp.toPx()
                    val s = 1f - 0.35f * fly.value
                    scaleX = s
                    scaleY = s
                    rotationZ = -10f * fly.value
                    alpha = if (fly.value == 0f) 0f else 1f - sticker.value
                }
                .size(width = 72.dp, height = 120.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(3.dp, Color.White, RoundedCornerShape(10.dp)),
        ) { MemeScreen() }
        // El sticker resultante
        StickerBubble(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 18.dp)
                .scale(sticker.value)
                .rotate(-8f),
            emoji = "😹",
            label = "JAJA",
            size = 104.dp,
        )
        // El GIF
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 26.dp, end = 34.dp)
                .scale(gif.value)
                .rotate(7f)
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Ink)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("GIF ▶", color = Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        // "Enviado" con los dos checks de WhatsApp
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-110).dp, y = (-2).dp)
                .scale(sent.value)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("Enviado ✓✓", color = Color(0xFF34B7F1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Phone(
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .size(width, height)
            .shadow(14.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Ink)
            .padding(5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White),
    ) {
        content()
        // Cámara frontal
        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(7.dp).clip(CircleShape).background(Ink))
    }
}

/** Pantalla de mentira: un chat con un meme. */
@Composable
private fun MemeScreen() {
    Column(Modifier.fillMaxSize().background(Color(0xFFECE5DD))) {
        Box(Modifier.fillMaxWidth().height(22.dp).background(Teal))
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            Box(
                Modifier.fillMaxWidth().weight(1f, fill = false).height(70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(Yellow, Orange))),
                contentAlignment = Alignment.Center,
            ) { Text("😹", fontSize = 30.sp) }
            Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFDCF8C6)).align(Alignment.End))
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
        }
    }
}

/** Sticker con borde blanco grueso y sombra, como se ven en WhatsApp. */
@Composable
private fun StickerBubble(modifier: Modifier, emoji: String, label: String?, size: Dp) {
    Box(
        modifier
            .size(size)
            .shadow(10.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Yellow, Orange))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = (size.value * 0.38f).sp)
            if (label != null) Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.15f).sp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Paso 2: galería con las capturas marcadas
// ---------------------------------------------------------------------------------------------

@Composable
private fun PermissionHero() {
    val tiles = remember { List(9) { Animatable(0f) } }
    val badge = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        tiles.forEachIndexed { i, a -> launch { delay(i * 70L); a.animateTo(1f, spring(dampingRatio = 0.55f)) } }
        delay(800)
        badge.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
    }
    val screenshots = setOf(0, 4, 8)
    val colors = listOf(Yellow, Color(0xFFB388FF), Color(0xFF80D8FF), Color(0xFFFF8A80), Orange, Color(0xFFA7FFEB), Color(0xFFFFE57F), Color(0xFFCCFF90), Pink)
    val emojis = listOf("😂", "🌅", "🐶", "🍕", "🤯", "🏖️", "🎂", "⚽", "💅")
    Box(contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 3) {
                        val i = row * 3 + col
                        Box(
                            Modifier
                                .size(58.dp)
                                .scale(tiles[i].value)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors[i])
                                .then(if (i in screenshots) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(emojis[i], fontSize = 24.sp)
                            if (i in screenshots) {
                                Text(
                                    "✓",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp)
                                        .scale(badge.value).clip(CircleShape).background(Green).padding(horizontal = 5.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Candado: las fotos se quedan en el móvil
        Box(
            Modifier
                .offset(x = 100.dp, y = 76.dp)
                .scale(badge.value)
                .size(54.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Text("🔒", fontSize = 26.sp) }
    }
}

// ---------------------------------------------------------------------------------------------
// Paso 3: dedos que pulsan los botones del móvil
// ---------------------------------------------------------------------------------------------

@Composable
private fun PressButtonsHero(powerOnTop: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulsar")
    // 0 = sin pulsar, 1 = pulsando. Ciclo de 2 s: espera, pulsa, mantiene un instante, suelta.
    val press by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2000
                0f at 0
                0f at 700
                1f at 900 using FastOutSlowInEasing
                1f at 1150
                0f at 1400
            },
        ),
        label = "press",
    )
    val flash by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2000
                0f at 0
                0f at 950
                0.95f at 1000
                0f at 1450
            },
        ),
        label = "flash",
    )

    val phoneH = 220.dp
    // Posición de cada botón (fracción del alto del móvil)
    val volumeY = if (powerOnTop) 0.42f else 0.2f
    val powerY = if (powerOnTop) 0.2f else 0.52f
    val glow = Yellow.copy(alpha = 0.4f + 0.6f * press)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 130.dp, height = phoneH)) {
            Phone(Modifier.align(Alignment.CenterStart), width = 120.dp, height = phoneH) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)))), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", fontSize = 40.sp, modifier = Modifier.scale(1f + 0.15f * press))
                        Text("¡clic!", fontWeight = FontWeight.Black, color = Orange, fontSize = 16.sp, modifier = Modifier.graphicsLayer { alpha = flash })
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash)))
            }
            // Botones laterales: se iluminan y se hunden al pulsar
            SideButton(Modifier.align(Alignment.TopEnd), y = phoneH * volumeY, length = 52.dp, color = glow, press = press)
            SideButton(Modifier.align(Alignment.TopEnd), y = phoneH * powerY, length = 30.dp, color = glow, press = press)
        }
        // Dedos y etiquetas
        Box(Modifier.size(width = 150.dp, height = phoneH)) {
            FingerLabel(y = phoneH * (powerY + 0.06f) - 18.dp, text = "Encendido", press = press)
            FingerLabel(y = phoneH * (volumeY + 0.16f) - 18.dp, text = "Bajar volumen", press = press)
        }
    }
}

@Composable
private fun SideButton(modifier: Modifier, y: Dp, length: Dp, color: Color, press: Float) {
    Box(
        modifier
            .offset(x = (-4).dp - (2.dp * press), y = y)
            .size(width = 8.dp, height = length)
            .shadow((6 * press).dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

@Composable
private fun FingerLabel(y: Dp, text: String, press: Float) {
    Row(
        Modifier.offset(x = (-6).dp * press, y = y),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("👈", fontSize = 30.sp)
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WaitingChip() {
    val pulse by rememberInfiniteTransition(label = "espera").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse",
    )
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Orange.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).scale(0.7f + 0.3f * pulse).clip(CircleShape).background(Orange.copy(alpha = pulse)))
        Spacer(Modifier.width(10.dp))
        Text("Esperando tu captura…", fontWeight = FontWeight.SemiBold)
    }
}

private data class BrandTips(val name: String, val tip: String?, val powerOnTop: Boolean)

/** Atajos de captura según el fabricante. Muchos gestos hay que activarlos antes en Ajustes. */
private fun brandTips(): BrandTips {
    val maker = Build.MANUFACTURER.lowercase(Locale.ROOT)
    val name = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return when {
        "samsung" in maker -> BrandTips(
            "Samsung",
            "También puedes deslizar el canto de la mano por la pantalla, de lado a lado. " +
                "Se activa en Ajustes › Funciones avanzadas › Movimientos y gestos › Deslizar palma para capturar.",
            powerOnTop = false,
        )
        listOf("xiaomi", "redmi", "poco").any { it in maker } -> BrandTips(
            "Xiaomi",
            "También puedes deslizar tres dedos hacia abajo por la pantalla, si tienes ese gesto activado.",
            powerOnTop = false,
        )
        listOf("oppo", "realme", "oneplus").any { it in maker } -> BrandTips(
            name,
            "También puedes deslizar tres dedos hacia abajo por la pantalla, si tienes ese gesto activado.",
            powerOnTop = true,
        )
        listOf("huawei", "honor").any { it in maker } -> BrandTips(
            name,
            "También puedes dar dos toques seguidos en la pantalla con el nudillo, si lo tienes activado.",
            powerOnTop = false,
        )
        "motorola" in maker -> BrandTips(
            "Motorola",
            "También puedes mantener tres dedos sobre la pantalla, si tienes ese gesto activado.",
            powerOnTop = true,
        )
        "google" in maker -> BrandTips(
            "Pixel",
            "También puedes abrir las apps recientes (desliza desde abajo y mantén) y tocar «Captura de pantalla».",
            powerOnTop = true,
        )
        else -> BrandTips(name, null, powerOnTop = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Paso 4: la captura convertida en sticker, con confeti
// ---------------------------------------------------------------------------------------------

private data class Confetti(val x: Float, val speed: Float, val phase: Float, val size: Float, val color: Color, val spin: Float)

@Composable
private fun DoneHero(capture: Uri?) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow)) }

    val pieces = remember {
        val palette = listOf(Yellow, Pink, Color.White, Purple, Orange, Color(0xFF80D8FF))
        List(70) {
            Confetti(
                x = Random.nextFloat(),
                speed = 0.5f + Random.nextFloat(),
                phase = Random.nextFloat(),
                size = 5f + Random.nextFloat() * 7f,
                color = palette.random(),
                spin = Random.nextFloat() * 360f,
            )
        }
    }
    val t by rememberInfiniteTransition(label = "confeti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t",
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (p in pieces) {
                val progress = (t * p.speed + p.phase) % 1f
                val x = size.width * p.x + sin((progress * 6 + p.phase * 10).toDouble()).toFloat() * 12.dp.toPx()
                val y = progress * (size.height + 40.dp.toPx()) - 20.dp.toPx()
                rotate(p.spin + progress * 720f, Offset(x, y)) {
                    drawRect(p.color, Offset(x, y), Size(p.size.dp.toPx(), p.size.dp.toPx() * 0.5f))
                }
            }
        }
        if (capture != null) {
            // La captura con borde blanco de sticker
            Box(
                Modifier
                    .scale(pop.value)
                    .rotate(-6f)
                    .shadow(16.dp, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                AsyncImage(
                    model = capture,
                    contentDescription = "Tu captura",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 150.dp, height = 200.dp),
                )
            }
        } else {
            StickerBubble(Modifier.scale(pop.value).rotate(-6f), emoji = "🎉", label = null, size = 150.dp)
        }
    }
}
