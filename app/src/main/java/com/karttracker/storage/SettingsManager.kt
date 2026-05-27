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
        
        private const val KEY_IMU_GAIN = "imu_gain"
        private const val KEY_COMPLEMENTARY_FILTER_ALPHA = "complementary_filter_alpha"
        private const val KEY_GAUSSIAN_SMOOTH_SIGMA = "gaussian_smooth_sigma"
        
        const val DEFAULT_API_TOKEN = ""
        const val DEFAULT_ZOOM_LEVEL = 17
        const val DEFAULT_AUTO_ZOOM = true
        const val DEFAULT_THEME_MODE = "system"
        
        const val THEME_MODE_LIGHT = "light"
        const val THEME_MODE_DARK = "dark"
        const val THEME_MODE_SYSTEM = "system"
        
        const val DEFAULT_IMU_GAIN = 0.3f
        const val DEFAULT_COMPLEMENTARY_FILTER_ALPHA = 0.3f
        const val DEFAULT_GAUSSIAN_SMOOTH_SIGMA = 50f
        
        const val MIN_IMU_GAIN = 0.01f
        const val MAX_IMU_GAIN = 1.0f
        const val MIN_FILTER_ALPHA = 0.01f
        const val MAX_FILTER_ALPHA = 1.0f
        const val MIN_SMOOTH_SIGMA = 1f
        const val MAX_SMOOTH_SIGMA = 100f
    }
    
    var mapApiToken: String
        get() = prefs.getString(KEY_MAP_API_TOKEN, DEFAULT_API_TOKEN) ?: DEFAULT_API_TOKEN
        set(value) = prefs.edit().putString(KEY_MAP_API_TOKEN, value).apply()
    
    var zoomLevel: Int
        get() = prefs.getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL)
        set(value) = prefs.edit().putInt(KEY_ZOOM_LEVEL, value.coerceIn(15, 19)).apply()
    
    var autoZoom: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ZOOM, DEFAULT_AUTO_ZOOM)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ZOOM, value).apply()
    
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE) ?: DEFAULT_THEME_MODE
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()
    
    var imuGain: Float
        get() = prefs.getFloat(KEY_IMU_GAIN, DEFAULT_IMU_GAIN)
        set(value) = prefs.edit().putFloat(KEY_IMU_GAIN, value.coerceIn(MIN_IMU_GAIN, MAX_IMU_GAIN)).apply()
    
    var complementaryFilterAlpha: Float
        get() = prefs.getFloat(KEY_COMPLEMENTARY_FILTER_ALPHA, DEFAULT_COMPLEMENTARY_FILTER_ALPHA)
        set(value) = prefs.edit().putFloat(KEY_COMPLEMENTARY_FILTER_ALPHA, value.coerceIn(MIN_FILTER_ALPHA, MAX_FILTER_ALPHA)).apply()
    
    var gaussianSmoothSigma: Float
        get() = prefs.getFloat(KEY_GAUSSIAN_SMOOTH_SIGMA, DEFAULT_GAUSSIAN_SMOOTH_SIGMA)
        set(value) = prefs.edit().putFloat(KEY_GAUSSIAN_SMOOTH_SIGMA, value.coerceIn(MIN_SMOOTH_SIGMA, MAX_SMOOTH_SIGMA)).apply()
    
    fun hasMapApiToken(): Boolean {
        return mapApiToken.isNotBlank()
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
