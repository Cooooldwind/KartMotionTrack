package com.karttracker

import android.os.Bundle
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.karttracker.storage.SettingsManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    
    private lateinit var etApiToken: TextInputEditText
    private lateinit var cbAutoZoom: CheckBox
    private lateinit var sliderZoom: Slider
    private lateinit var tvZoomLevel: TextView
    private lateinit var rgThemeMode: RadioGroup
    
    private lateinit var sliderImuGain: Slider
    private lateinit var tvImuGain: TextView
    private lateinit var sliderFilterAlpha: Slider
    private lateinit var tvFilterAlpha: TextView
    private lateinit var sliderGaussianSigma: Slider
    private lateinit var tvGaussianSigma: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"
        
        settingsManager = SettingsManager(this)
        
        initViews()
        loadSettings()
        setupListeners()
    }
    
    private fun initViews() {
        etApiToken = findViewById(R.id.etApiToken)
        cbAutoZoom = findViewById(R.id.cbAutoZoom)
        sliderZoom = findViewById(R.id.sliderZoom)
        tvZoomLevel = findViewById(R.id.tvZoomLevel)
        rgThemeMode = findViewById(R.id.rgThemeMode)
        
        sliderImuGain = findViewById(R.id.sliderImuGain)
        tvImuGain = findViewById(R.id.tvImuGain)
        sliderFilterAlpha = findViewById(R.id.sliderFilterAlpha)
        tvFilterAlpha = findViewById(R.id.tvFilterAlpha)
        sliderGaussianSigma = findViewById(R.id.sliderGaussianSigma)
        tvGaussianSigma = findViewById(R.id.tvGaussianSigma)
    }
    
    private fun loadSettings() {
        etApiToken.setText(settingsManager.mapApiToken)
        cbAutoZoom.isChecked = settingsManager.autoZoom
        sliderZoom.value = settingsManager.zoomLevel.toFloat()
        updateZoomLevelText(settingsManager.zoomLevel)
        
        when (settingsManager.themeMode) {
            SettingsManager.THEME_MODE_LIGHT -> rgThemeMode.check(R.id.rbLight)
            SettingsManager.THEME_MODE_DARK -> rgThemeMode.check(R.id.rbDark)
            else -> rgThemeMode.check(R.id.rbSystem)
        }
        
        sliderImuGain.value = settingsManager.imuGain
        updateImuGainText(settingsManager.imuGain)
        sliderFilterAlpha.value = settingsManager.complementaryFilterAlpha
        updateFilterAlphaText(settingsManager.complementaryFilterAlpha)
        sliderGaussianSigma.value = settingsManager.gaussianSmoothSigma
        updateGaussianSigmaText(settingsManager.gaussianSmoothSigma.toInt())
        
        updateSliderState()
    }
    
    private fun setupListeners() {
        cbAutoZoom.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.autoZoom = isChecked
            updateSliderState()
        }
        
        sliderZoom.addOnChangeListener { _, value, _ ->
            val level = value.toInt()
            settingsManager.zoomLevel = level
            updateZoomLevelText(level)
        }
        
        rgThemeMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbLight -> SettingsManager.THEME_MODE_LIGHT
                R.id.rbDark -> SettingsManager.THEME_MODE_DARK
                else -> SettingsManager.THEME_MODE_SYSTEM
            }
            settingsManager.themeMode = mode
            applyTheme(mode)
        }
        
        sliderImuGain.addOnChangeListener { _, value, _ ->
            settingsManager.imuGain = value
            updateImuGainText(value)
        }
        
        sliderFilterAlpha.addOnChangeListener { _, value, _ ->
            settingsManager.complementaryFilterAlpha = value
            updateFilterAlphaText(value)
        }
        
        sliderGaussianSigma.addOnChangeListener { _, value, _ ->
            settingsManager.gaussianSmoothSigma = value
            updateGaussianSigmaText(value.toInt())
        }
        
        etApiToken.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveApiToken()
            }
        }
    }
    
    private fun saveApiToken() {
        val token = etApiToken.text.toString().trim()
        settingsManager.mapApiToken = token
        
        if (token.isNotBlank()) {
            Toast.makeText(this, "API Token已保存", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateSliderState() {
        sliderZoom.isEnabled = !cbAutoZoom.isChecked
        sliderZoom.alpha = if (cbAutoZoom.isChecked) 0.5f else 1.0f
    }
    
    private fun updateZoomLevelText(level: Int) {
        val quality = when (level) {
            15 -> "低"
            16 -> "中低"
            17 -> "中"
            18 -> "中高"
            19 -> "高"
            else -> "中"
        }
        tvZoomLevel.text = "当前精度：${level}级 ($quality)"
    }
    
    private fun updateImuGainText(value: Float) {
        tvImuGain.text = String.format("当前值：%.2f", value)
    }
    
    private fun updateFilterAlphaText(value: Float) {
        tvFilterAlpha.text = String.format("当前值：%.2f", value)
    }
    
    private fun updateGaussianSigmaText(value: Int) {
        tvGaussianSigma.text = "当前值：$value"
    }
    
    private fun applyTheme(mode: String) {
        val nightMode = when (mode) {
            SettingsManager.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsManager.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    override fun onPause() {
        super.onPause()
        saveApiToken()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
