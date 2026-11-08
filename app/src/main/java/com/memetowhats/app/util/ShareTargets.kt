package com.memetowhats.app.util

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Una app de mensajería instalada a la que se puede enviar directamente. */
data class ShareTarget(val packageName: String, val label: String, val icon: ImageBitmap) {
    val isWhatsApp get() = packageName in WHATSAPP_PACKAGES
}

val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

object ShareTargets {
    /** Apps de mensajería conocidas, en el orden en que se muestran. */
    private val messaging = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca", // Messenger
        "com.instagram.android",
        "com.discord",
        "com.google.android.apps.messaging", // Mensajes de Google
        "com.samsung.android.messaging",
        "com.snapchat.android",
        "jp.naver.line.android",
        "com.viber.voip",
        "com.microsoft.teams",
        "com.Slack",
    )

    /** Las apps de mensajería instaladas que aceptan archivos de tipo [mimeType]. */
    fun load(context: Context, mimeType: String): List<ShareTarget> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_SEND).setType(mimeType)
        @Suppress("DEPRECATION")
        val canReceive = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        return messaging.filter { it in canReceive }.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                ShareTarget(
                    packageName = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap(),
                )
            }.getOrNull()
        }
    }
}
