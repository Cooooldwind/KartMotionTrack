package com.karttracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class HelpActivity : AppCompatActivity() {
    
    private lateinit var btnOpenWebsite: MaterialButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "帮助"
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        btnOpenWebsite = findViewById(R.id.btnOpenWebsite)
    }
    
    private fun setupListeners() {
        btnOpenWebsite.setOnClickListener {
            openGeoVisEarthWebsite()
        }
    }
    
    private fun openGeoVisEarthWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://datacloud.geovisearth.com/")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接，请手动访问", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
