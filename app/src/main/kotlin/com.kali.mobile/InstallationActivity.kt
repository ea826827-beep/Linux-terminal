package com.kali.mobile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Installation activity for Kali Linux ARM64 rootless userspace.
 * Provides a premium installation interface with progress tracking.
 *
 * Features:
 * - Kali Linux installation status
 * - Download progress with percentage
 * - Extraction progress
 * - Initialization progress
 * - Retry button on failure
 * - Error details
 * - Real PRoot binary integration
 *
 * Does NOT claim installation succeeded before verification.
 * Does NOT use simulated output.
 */
class InstallationActivity : AppCompatActivity(), KaliProgressListener {

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var retryButton: Button
    private lateinit var installButton: Button
    private lateinit var errorText: TextView

    private val kaliManager = KaliManager(this)
    private val handler = Handler(Looper.getMainLooper())
    private var isInstalling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installation)

        // Initialize views
        statusText = findViewById(R.id.installStatusText)
        progressText = findViewById(R.id.installProgressText)
        progressBar = findViewById(R.id.installProgressBar)
        retryButton = findViewById(R.id.btnRetry)
        installButton = findViewById(R.id.btnInstall)
        errorText = findViewById(R.id.installErrorText)

        // Check current installation status
        updateInstallationUI()

        // Install button
        installButton.setOnClickListener {
            startInstallation()
        }

        // Retry button
        retryButton.setOnClickListener {
            errorText.visibility = View.GONE
            retryButton.visibility = View.GONE
            installButton.visibility = View.VISIBLE
            startInstallation()
        }
    }

    /** Close installation activity */
    fun onCloseClick(view: View) {
        finish()
    }

    private fun updateInstallationUI() {
        val isInitialized = kaliManager.isEnvironmentInitialized()
        val prootAvailable = kaliManager.isProotAvailable()

        if (isInitialized) {
            statusText.text = "Kali Linux Installed"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            progressText.text = "Rootless ARM64 userspace ready"
            progressBar.progress = 100
            installButton.text = "Reinstall Kali Linux"
            installButton.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            errorText.visibility = View.GONE
        } else if (prootAvailable) {
            statusText.text = "PRoot Ready"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            progressText.text = "PRoot binary available. Ready to install Kali."
            progressBar.progress = 0
            installButton.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            errorText.visibility = View.GONE
        } else {
            statusText.text = "Not Installed"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            progressText.text = "Click Install to download Kali Linux ARM64"
            progressBar.progress = 0
            installButton.visibility = View.VISIBLE
            retryButton.visibility = View.GONE
            errorText.visibility = View.GONE
        }
    }

    private fun startInstallation() {
        if (isInstalling) return

        isInstalling = true
        installButton.visibility = View.GONE
        retryButton.visibility = View.GONE
        errorText.visibility = View.GONE
        progressBar.progress = 0
        progressText.text = "Starting installation..."
        statusText.text = "Installing Kali Linux..."
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        // Run installation on background thread
        Thread {
            try {
                val success = kaliManager.initializeEnvironment(this@InstallationActivity)

                handler.post {
                    isInstalling = false
                    if (success) {
                        updateInstallationUI()
                    } else {
                        installButton.visibility = View.VISIBLE
                        retryButton.visibility = View.VISIBLE
                        statusText.text = "Installation Failed"
                        statusText.setTextColor(ContextCompat.getColor(this@InstallationActivity, android.R.color.holo_red_light))
                        progressText.text = "Installation did not complete successfully"
                    }
                }
            } catch (e: Exception) {
                Log.e("InstallationActivity", "Installation error", e)
                handler.post {
                    isInstalling = false
                    installButton.visibility = View.VISIBLE
                    retryButton.visibility = View.VISIBLE
                    statusText.text = "Installation Error"
                    statusText.setTextColor(ContextCompat.getColor(this@InstallationActivity, android.R.color.holo_red_light))
                    progressText.text = "An unexpected error occurred"
                    errorText.text = "Error: ${e.message}"
                    errorText.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    // KaliProgressListener implementation
    override fun onStatus(message: String) {
        handler.post {
            statusText.text = message
        }
    }

    override fun onProgress(percent: Int, bytesRead: Long, totalLength: Long) {
        handler.post {
            progressBar.progress = percent
            val mbRead = bytesRead / (1024 * 1024)
            val mbTotal = if (totalLength > 0) totalLength / (1024 * 1024) else 0L
            progressText.text = "$percent% ($mbRead MB / $mbTotal MB)"
        }
    }

    override fun onError(message: String) {
        handler.post {
            errorText.text = message
            errorText.visibility = View.VISIBLE
            retryButton.visibility = View.VISIBLE
            installButton.visibility = View.GONE
            isInstalling = false
            statusText.text = "Installation Failed"
            statusText.setTextColor(ContextCompat.getColor(this@InstallationActivity, android.R.color.holo_red_light))
        }
    }

    override fun onComplete() {
        handler.post {
            isInstalling = false
            updateInstallationUI()
        }
    }
}