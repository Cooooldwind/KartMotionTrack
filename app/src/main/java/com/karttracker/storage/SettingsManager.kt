package com.karttracker.storage

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "kart_tracker_settings"
        
        private const val KEY_MAP_API_TOKEN = "map_api_token"
        private const val KEY_ZOOM_LEVEL = "zoom_level"
        private const val KEY_AUTO_ZOOM = "auto_zoom"
        private const val KEY_THEME_MODE = "theme_mode"
        
        const val DEFAULT_API_TOKEN = ""
        const val DEFAULT_ZOOM_LEVEL = 17
        const val DEFAULT_AUTO_ZOOM = true
        const val DEFAULT_THEME_MODE = "system"
        
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_SYSTEM = "system"
    }
    
    var mapApiToken: String
        get() = prefs.getString(KEY_MAP_API_TOKEN, DEFAULT_API_TOKEN) ?: DEFAULT_API_TOKEN
        set(value) = prefs.edit().putString(KEY_MAP_API_TOKEN, value).apply()
    
    var zoomLevel: Int
        get() = prefs.getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL)
        set(value) = prefs.edit().putInt(KEY_ZOOM_LEVEL, value.coerceIn(15, 18)).apply()
    
    var autoZoom: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ZOOM, DEFAULT_AUTO_ZOOM)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ZOOM, value).apply()
    
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()
    
    fun hasMapApiToken(): Boolean {
        return mapApiToken.isNotBlank()
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
