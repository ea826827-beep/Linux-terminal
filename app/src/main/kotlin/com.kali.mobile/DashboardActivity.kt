package com.kali.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Premium Dashboard Activity for KALI MOBILE.
 * Professional cybersecurity interface with bottom navigation.
 *
 * Features:
 * - KALI MOBILE branding with premium styling
 * - Kali Linux installation status
 * - System architecture (ARM64)
 * - Linux environment status
 * - Storage usage information
 * - Installed package count
 * - Quick terminal launch
 * - Recently used tools
 * - Bottom navigation (Home, Terminal, Tools, Files, Settings)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var kaliManager: KaliManager
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var kaliStatusText: TextView
    private lateinit var kaliStatusDot: View
    private lateinit var storageText: TextView
    private lateinit var packageCountText: TextView
    private lateinit var archText: TextView
    private lateinit var envStatusText: TextView
    private lateinit var handler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        kaliManager = KaliManager(this)
        handler = Handler(Looper.getMainLooper())

        // Initialize views
        kaliStatusText = findViewById(R.id.kaliStatusText)
        kaliStatusDot = findViewById(R.id.kaliStatusDot)
        storageText = findViewById(R.id.storageText)
        packageCountText = findViewById(R.id.packageCountText)
        archText = findViewById(R.id.archText)
        envStatusText = findViewById(R.id.envStatusText)
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Setup bottom navigation
        setupBottomNavigation()

        // Update dashboard info
        updateDashboardInfo()

        // Quick action buttons
        findViewById<Button>(R.id.btnLaunchTerminal).setOnClickListener {
            launchTerminal()
        }

        findViewById<Button>(R.id.btnInstallKali).setOnClickListener {
            launchInstallation()
        }

        findViewById<Button>(R.id.btnFileManager).setOnClickListener {
            launchFileManager()
        }

        findViewById<Button>(R.id.btnPackageManager).setOnClickListener {
            launchPackageManager()
        }

        findViewById<Button>(R.id.btnPython).setOnClickListener {
            launchPython()
        }

        findViewById<Button>(R.id.btnGit).setOnClickListener {
            launchGit()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            launchSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboardInfo()
    }

    /**
     * Setup bottom navigation bar.
     */
    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_terminal -> {
                    launchTerminal()
                    true
                }
                R.id.nav_tools -> {
                    showToolsMenu()
                    true
                }
                R.id.nav_files -> {
                    launchFileManager()
                    true
                }
                R.id.nav_settings -> {
                    launchSettings()
                    true
                }
                else -> false
            }
        }
        // Set home as selected
        bottomNavigation.selectedItemId = R.id.nav_home
    }

    /**
     * Update dashboard information from KaliManager.
     * Wrapped in try/catch so the app never crashes on open,
     * even if Kali is not installed yet.
     */
    private fun updateDashboardInfo() {
        try {
            // Kali status
            val isInitialized = kaliManager.isEnvironmentInitialized()
            kaliStatusText.text = if (isInitialized) "Installed" else "Not installed"
            kaliStatusText.setTextColor(
                if (isInitialized) android.graphics.Color.parseColor("#32CD32")
                else android.graphics.Color.parseColor("#888888")
            )
            kaliStatusDot.setBackgroundColor(
                if (isInitialized) android.graphics.Color.parseColor("#32CD32")
                else android.graphics.Color.parseColor("#FF5555")
            )

            // Storage info
            val availableSpace = kaliManager.getAvailableSpace()
            val availableGB = availableSpace / (1024L * 1024L * 1024L)
            storageText.text = "${availableGB} GB available"

            // Architecture
            val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            archText.text = arch

            // Environment status
            val prootAvailable = kaliManager.isProotAvailable()
            envStatusText.text = if (isInitialized && prootAvailable) "Ready" else "Not ready"
            envStatusText.setTextColor(
                if (isInitialized && prootAvailable) android.graphics.Color.parseColor("#32CD32")
                else android.graphics.Color.parseColor("#FF5555")
            )

            // Package count - try to get from Kali if installed
            if (isInitialized && prootAvailable) {
                Thread {
                    try {
                        val pkgOutput = kaliManager.executeCommand("dpkg -l 2>/dev/null | grep -c '^ii'")
                        handler.post {
                            val count = pkgOutput?.trim()?.toIntOrNull() ?: 0
                            packageCountText.text = "$count packages"
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error counting packages", e)
                    }
                }.start()
            } else {
                packageCountText.text = "0 packages"
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating dashboard info", e)
            // Fallback UI so the dashboard never shows blank on error
            try {
                kaliStatusText.text = "Unknown"
                envStatusText.text = "Not ready"
                packageCountText.text = "0 packages"
            } catch (ignored: Exception) {
                // Views may not be ready yet; nothing else to do
            }
        }
    }

    private fun launchTerminal() {
        val intent = Intent(this, MultiSessionTerminalActivity::class.java)
        startActivity(intent)
    }

    private fun launchInstallation() {
        val intent = Intent(this, InstallationActivity::class.java)
        startActivity(intent)
    }

    private fun launchFileManager() {
        val intent = Intent(this, FileManagerActivity::class.java)
        startActivity(intent)
    }

    private fun launchPackageManager() {
        val intent = Intent(this, PackageManagerActivity::class.java)
        startActivity(intent)
    }

    private fun launchPython() {
        val intent = Intent(this, PythonActivity::class.java)
        startActivity(intent)
    }

    private fun launchGit() {
        val intent = Intent(this, GitActivity::class.java)
        startActivity(intent)
    }

    private fun launchSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun showToolsMenu() {
        // Show a quick tools selection dialog
        val tools = arrayOf(
            "Python 3" to "python3 --version",
            "Git" to "git --version",
            "Nano" to "nano --version",
            "Vim" to "vim --version",
            "cURL" to "curl --version",
            "Wget" to "wget --version"
        )

        val names = tools.map { it.first }
        AlertDialog.Builder(this)
            .setTitle("Quick Tools")
            .setItems(names.toTypedArray()) { _, which ->
                val (_, cmd) = tools[which]
                runQuickTool(cmd)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runQuickTool(command: String) {
        if (!kaliManager.isEnvironmentInitialized() || !kaliManager.isProotAvailable()) {
            // Show error - Kali not installed
            android.widget.Toast.makeText(
                this,
                "Kali Linux not installed. Install from Dashboard first.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }

        // Launch terminal with the command pre-filled
        val intent = Intent(this, MultiSessionTerminalActivity::class.java)
        intent.putExtra("quick_command", command)
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }
}