package com.example.wififileshare

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private var server: FileServer? = null
    private val serverPort = 8080
    private var isServerRunning = false

    private lateinit var tvStatusBadge: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvServerUrl: TextView
    private lateinit var btnCopyUrl: Button
    private lateinit var btnToggleServer: Button
    private lateinit var cardPermission: View
    private lateinit var btnGrantPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateNetworkInfo()
    }

    override fun onResume() {
        super.onResume()
        checkAndShowPermissionStatus()
        updateNetworkInfo()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    private fun initViews() {
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvIpAddress = findViewById(R.id.tvIpAddress)
        tvServerUrl = findViewById(R.id.tvServerUrl)
        btnCopyUrl = findViewById(R.id.btnCopyUrl)
        btnToggleServer = findViewById(R.id.btnToggleServer)
        cardPermission = findViewById(R.id.cardPermission)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
    }

    private fun setupListeners() {
        btnToggleServer.setOnClickListener {
            if (isServerRunning) {
                stopServer()
            } else {
                startServer()
            }
        }

        btnCopyUrl.setOnClickListener {
            val url = tvServerUrl.text.toString()
            if (url.startsWith("http://")) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Server URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Alamat berhasil disalin!", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantPermission.setOnClickListener {
            requestStoragePermissions()
        }
    }

    private fun updateNetworkInfo() {
        val ip = NetworkUtils.getLocalWifiIpAddress()
        if (ip != null) {
            tvIpAddress.text = "IP: $ip"
        } else {
            tvIpAddress.text = "IP: Menunggu Wi-Fi"
        }
    }

    private fun startServer() {
        val ip = NetworkUtils.getLocalWifiIpAddress()
        if (ip == null) {
            Toast.makeText(
                this,
                "Ponsel belum terhubung ke Wi-Fi!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val sharedDir = getSharedDirectory()

        try {
            server = FileServer(sharedDir, serverPort)
            server?.start()
            isServerRunning = true

            val fullUrl = "http://$ip:$serverPort"

            // Minimalist 2-Color State: Invert badge to solid black pill with white text
            tvStatusBadge.text = "ONLINE"
            tvStatusBadge.setTextColor(Color.WHITE)
            tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_badge_solid)

            tvServerUrl.text = fullUrl
            tvServerUrl.setTextColor(Color.BLACK)
            btnCopyUrl.isEnabled = true

            btnToggleServer.text = getString(R.string.btn_stop)

            Toast.makeText(this, "Server aktif!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memulai server: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        try {
            server?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        server = null
        isServerRunning = false

        // Minimalist 2-Color State: Outline badge with black text
        tvStatusBadge.text = "STANDBY"
        tvStatusBadge.setTextColor(Color.BLACK)
        tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_badge_outline)

        tvServerUrl.text = "http://192.168.x.x:$serverPort"
        tvServerUrl.setTextColor(Color.parseColor("#4B5563"))
        btnCopyUrl.isEnabled = false

        btnToggleServer.text = getString(R.string.btn_start)
    }

    private fun getSharedDirectory(): File {
        if (hasFullStoragePermission()) {
            val root = Environment.getExternalStorageDirectory()
            if (root.canRead()) return root
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    private fun hasFullStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndShowPermissionStatus() {
        if (!hasFullStoragePermission()) {
            cardPermission.visibility = View.VISIBLE
        } else {
            cardPermission.visibility = View.GONE
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }
}
