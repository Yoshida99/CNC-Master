package com.yoshida.cncmaster.ai

import android.content.Context
import java.util.UUID

object AiInstallationId {
    private const val PREFS = "cnc_ai_identity"
    private const val KEY_INSTALLATION_ID = "installation_id"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

        return synchronized(this) {
            prefs.getString(KEY_INSTALLATION_ID, null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also {
                    prefs.edit().putString(KEY_INSTALLATION_ID, it).apply()
                }
        }
    }
}
