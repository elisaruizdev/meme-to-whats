package com.memetowhats.app.util

import android.content.Context

/** Ajustes pequeños de la app (SharedPreferences). */
object AppPrefs {
    private const val FILE = "app_prefs"
    private const val ONBOARDING_DONE = "onboarding_done"
    private const val REPLACE_ORIGINAL = "replace_original"
    private const val LAST_CLIP_USED = "last_clip_used"
    private const val PACKS_IN_WHATSAPP = "packs_in_whatsapp"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun onboardingDone(context: Context) = prefs(context).getBoolean(ONBOARDING_DONE, false)

    fun setOnboardingDone(context: Context) = prefs(context).edit().putBoolean(ONBOARDING_DONE, true).apply()

    /** Al crear, quitar la captura original de la galería y quedarse solo con el sticker o GIF. */
    fun replaceOriginal(context: Context) = prefs(context).getBoolean(REPLACE_ORIGINAL, true)

    fun setReplaceOriginal(context: Context, value: Boolean) = prefs(context).edit().putBoolean(REPLACE_ORIGINAL, value).apply()

    /** Hora de la última imagen del portapapeles ya usada o descartada, para no volver a ofrecerla. */
    fun lastClipUsed(context: Context) = prefs(context).getLong(LAST_CLIP_USED, -1L)

    fun setLastClipUsed(context: Context, timestamp: Long) = prefs(context).edit().putLong(LAST_CLIP_USED, timestamp).apply()

    /** Packs que WhatsApp ya aceptó (por si su comprobación falla, no volver a pedir añadirlos). */
    fun packAddedToWhatsApp(context: Context, packId: String) =
        packId in prefs(context).getStringSet(PACKS_IN_WHATSAPP, emptySet()).orEmpty()

    fun setPackAddedToWhatsApp(context: Context, packId: String) {
        val packs = prefs(context).getStringSet(PACKS_IN_WHATSAPP, emptySet()).orEmpty() + packId
        prefs(context).edit().putStringSet(PACKS_IN_WHATSAPP, packs).apply()
    }
}
