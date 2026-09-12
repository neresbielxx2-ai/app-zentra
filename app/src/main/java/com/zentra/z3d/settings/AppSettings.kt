package com.zentra.z3d.settings

import android.app.ActivityManager
import android.content.Context

enum class Quality { LOW, MEDIUM, HIGH, AUTO }

data class AppSettings(
    var quality: Quality = Quality.AUTO,
    var shadows: Boolean = true,
    var polyLimit: Int = 150_000,
    var autosaveMinutes: Int = 5,
    var showGrid: Boolean = true,
    var orbitSensitivity: Float = 1f,
    var showStats: Boolean = false
)

object SettingsStore {
    private const val PREFS = "zentra_settings"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            quality = runCatching { Quality.valueOf(p.getString("quality", "AUTO") ?: "AUTO") }
                .getOrDefault(Quality.AUTO),
            shadows = p.getBoolean("shadows", true),
            polyLimit = p.getInt("polyLimit", 150_000).coerceIn(10_000, 1_000_000),
            autosaveMinutes = p.getInt("autosaveMinutes", 5).coerceIn(1, 30),
            showGrid = p.getBoolean("showGrid", true),
            orbitSensitivity = p.getFloat("orbitSensitivity", 1f).coerceIn(0.2f, 3f),
            showStats = p.getBoolean("showStats", false)
        )
    }

    fun save(context: Context, s: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("quality", s.quality.name)
            .putBoolean("shadows", s.shadows)
            .putInt("polyLimit", s.polyLimit)
            .putInt("autosaveMinutes", s.autosaveMinutes)
            .putBoolean("showGrid", s.showGrid)
            .putFloat("orbitSensitivity", s.orbitSensitivity)
            .putBoolean("showStats", s.showStats)
            .apply()
    }
}

object PerformanceManager {
    fun isLowRamDevice(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.isLowRamDevice
        } catch (_: Exception) { false }
    }

    fun effectiveQuality(settings: AppSettings, context: Context): Quality {
        if (settings.quality != Quality.AUTO) return settings.quality
        return if (isLowRamDevice(context)) Quality.LOW else Quality.MEDIUM
    }

    /** Maximo de luzes dinamicas por qualidade. */
    fun maxLights(q: Quality): Int = when (q) {
        Quality.LOW -> 2
        Quality.MEDIUM -> 4
        Quality.HIGH, Quality.AUTO -> 8
    }

    /** Escala de resolucao interna (1 = nativa). */
    fun renderScale(q: Quality): Float = when (q) {
        Quality.LOW -> 0.7f
        Quality.MEDIUM -> 0.9f
        Quality.HIGH, Quality.AUTO -> 1f
    }
}
