package com.memetowhats.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    /** Menú nativo de compartir de Android (Telegram, Instagram, etc.). */
    fun withChooser(context: Context, file: File, mimeType: String) {
        context.startActivity(Intent.createChooser(sendIntent(context, file, mimeType), "Enviar con…"))
    }

    /** Envía directamente a una app concreta; si falla, abre el menú de compartir. */
    fun toApp(context: Context, file: File, mimeType: String, packageName: String) {
        try {
            context.startActivity(sendIntent(context, file, mimeType).setPackage(packageName))
        } catch (_: ActivityNotFoundException) {
            withChooser(context, file, mimeType)
        }
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
