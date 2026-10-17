package com.memetowhats.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    private val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Abre WhatsApp (o WhatsApp Business) con el archivo listo para elegir el chat. */
    fun toWhatsApp(context: Context, file: File, mimeType: String) {
        for (pkg in whatsappPackages) {
            try {
                context.startActivity(sendIntent(context, file, mimeType).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // Probamos con la siguiente variante de WhatsApp.
            }
        }
        Toast.makeText(context, "WhatsApp no está instalado", Toast.LENGTH_SHORT).show()
        withChooser(context, file, mimeType)
    }

    /** Menú nativo de compartir de Android (Telegram, Instagram, etc.). */
    fun withChooser(context: Context, file: File, mimeType: String) {
        context.startActivity(Intent.createChooser(sendIntent(context, file, mimeType), "Compartir con…"))
    }

    private fun sendIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
