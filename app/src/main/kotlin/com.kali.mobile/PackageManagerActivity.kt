package com.kali.mobile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Package Manager Activity for KALI MOBILE.
 * Provides a real APT package management interface connected to the Kali Linux environment.
 *
 * Features:
 * - apt update / upgrade
 * - Install / remove packages
 * - Search packages
 * - Show installed packages
 * - Real command output display
 * - Progress indicators
 * - Essential tools quick-install
 *
 * Does NOT simulate output. All results come from real apt commands.
 * Does NOT claim packages are installed until verification succeeds.
 */
class PackageManagerActivity : AppCompatActivity(), AptProgressListener {

    private lateinit var aptManager: KaliAptManager
    private lateinit var kaliManager: KaliManager
    private val handler = Handler(Looper.getMainLooper())

    // UI elements
    private lateinit var scrollView: ScrollView
    private lateinit var outputText: TextView
    private lateinit var searchEdit: EditText
    private lateinit var installEdit: EditText
    private lateinit var btnUpdate: Button
    private lateinit var btnUpgrade: Button
    private lateinit var btnInstall: Button
    private lateinit var btnSearch: Button
    private lateinit var btnListInstalled: Button
    private lateinit var btnEssentialTools: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var isOperationRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_package_manager)

        kaliManager = KaliManager(this)
        aptManager = KaliAptManager(this, kaliManager)

        // Initialize views
        scrollView = findViewById(R.id.pkgScrollView)
        outputText = findViewById(R.id.pkgOutputText)
        searchEdit = findViewById(R.id.pkgSearchEdit)
        installEdit = findViewById(R.id.pkgInstallEdit)
        btnUpdate = findViewById(R.id.btnAptUpdate)
        btnUpgrade = findViewById(R.id.btnAptUpgrade)
        btnInstall = findViewById(R.id.btnAptInstall)
        btnSearch = findViewById(R.id.btnPkgSearch)
        btnListInstalled = findViewById(R.id.btnListInstalled)
        btnEssentialTools = findViewById(R.id.btnEssentialTools)
        progressBar = findViewById(R.id.pkgProgressBar)
        statusText = findViewById(R.id.pkgStatusText)

        // Set monospace font for output
        outputText.typeface = android.graphics.Typeface.MONOSPACE

        // Check Kali status
        updateKaliStatus()

        // apt update button
        btnUpdate.setOnClickListener {
            runAptUpdate()
        }

        // apt upgrade button
        btnUpgrade.setOnClickListener {
            runAptUpgrade()
        }

        // Install package button
        btnInstall.setOnClickListener {
            val pkgName = installEdit.text.toString().trim()
            if (pkgName.isEmpty()) {
                Toast.makeText(this, "Enter a package name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runAptInstall(pkgName)
        }

        // Search button
        btnSearch.setOnClickListener {
            val query = searchEdit.text.toString().trim()
            if (query.isEmpty()) {
                Toast.makeText(this, "Enter a search term", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runPkgSearch(query)
        }

        // List installed packages
        btnListInstalled.setOnClickListener {
            runListInstalled()
        }

        // Essential tools button
        btnEssentialTools.setOnClickListener {
            showEssentialToolsDialog()
        }

        // Auto-scroll output
        outputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scrollToBottom()
            }
        })
    }

    private fun updateKaliStatus() {
        val isReady = aptManager.isReady()
        if (isReady) {
            statusText.text = "Kali Linux: Ready"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            btnUpdate.isEnabled = true
            btnUpgrade.isEnabled = true
            btnInstall.isEnabled = true
            btnSearch.isEnabled = true
            btnListInstalled.isEnabled = true
            btnEssentialTools.isEnabled = true
        } else {
            statusText.text = "Kali Linux: Not installed"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            btnUpdate.isEnabled = false
            btnUpgrade.isEnabled = false
            btnInstall.isEnabled = false
            btnSearch.isEnabled = false
            btnListInstalled.isEnabled = false
            btnEssentialTools.isEnabled = false
            appendOutput("Kali Linux environment not initialized. Install from Dashboard first.\n", isError = true)
        }
    }

    override fun onResume() {
        super.onResume()
        updateKaliStatus()
    }

    // ── APT Operations ──
    private fun runAptUpdate() {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> apt-get update\n", isError = false)

        Thread {
            try {
                val result = aptManager.aptUpdate()
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "apt update error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    private fun runAptUpgrade() {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> apt-get upgrade -y\n", isError = false)

        Thread {
            try {
                val result = aptManager.aptUpgrade()
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "apt upgrade error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    private fun runAptInstall(packageName: String) {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> apt-get install -y $packageName\n", isError = false)

        Thread {
            try {
                val result = aptManager.aptInstall(packageName)
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    // Verify installation
                    if (aptManager.isPackageInstalled(packageName)) {
                        appendOutput("✓ $packageName installed successfully\n", isError = false)
                    } else if (result.contains("E:") || result.contains("error")) {
                        appendOutput("✗ $packageName installation failed\n", isError = true)
                    }
                    isOperationRunning = false
                    setButtonsEnabled(true)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "apt install error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    private fun runPkgSearch(query: String) {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> apt-cache search '$query'\n", isError = false)

        Thread {
            try {
                val result = aptManager.searchPackages(query)
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "apt search error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    private fun runListInstalled() {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> dpkg -l (installed packages)\n", isError = false)

        Thread {
            try {
                val result = aptManager.listInstalledPackages()
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "list packages error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    // ── Essential Tools ──
    private fun showEssentialToolsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Install Essential Tools")
        builder.setMessage("Install: python3, git, nano, vim, curl, wget, openssh-client, build-essential")
        builder.setPositiveButton("Install All") { _, _ ->
            installEssentialTools()
        }
        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun installEssentialTools() {
        if (isOperationRunning) return
        isOperationRunning = true
        setButtonsEnabled(false)
        appendOutput(">>> Installing essential development tools...\n", isError = false)

        Thread {
            try {
                val results = aptManager.installEssentialTools(object : AptProgressListener {
                    override fun onStatus(message: String) {
                        handler.post {
                            statusText.text = message
                            appendOutput("$message\n", isError = false)
                            scrollToBottom()
                        }
                    }

                    override fun onPackageResult(packageName: String, result: String) {
                        handler.post {
                            appendOutput("$packageName: $result\n", isError = false)
                            scrollToBottom()
                        }
                    }

                    override fun onComplete(results: Map<String, String>) {
                        handler.post {
                            appendOutput("\n=== Essential Tools Installation Complete ===\n", isError = false)
                            for ((pkg, status) in results) {
                                appendOutput("  $pkg: $status\n", isError = false)
                            }
                            isOperationRunning = false
                            setButtonsEnabled(true)
                            scrollToBottom()
                        }
                    }

                    override fun onError(message: String) {
                        handler.post {
                            appendOutput("Error: $message\n", isError = true)
                            isOperationRunning = false
                            setButtonsEnabled(true)
                        }
                    }
                })

                // Verify key tools
                handler.post {
                    appendOutput("\n=== Verification ===\n", isError = false)
                    appendOutput("python3: ${if (aptManager.isToolAvailable("python3")) "✓ Available" else "✗ Not found"}\n", isError = false)
                    appendOutput("git: ${if (aptManager.isToolAvailable("git")) "✓ Available" else "✗ Not found"}\n", isError = false)
                    appendOutput("nano: ${if (aptManager.isToolAvailable("nano")) "✓ Available" else "✗ Not found"}\n", isError = false)
                    appendOutput("vim: ${if (aptManager.isToolAvailable("vim")) "✓ Available" else "✗ Not found"}\n", isError = false)
                    appendOutput("curl: ${if (aptManager.isToolAvailable("curl")) "✓ Available" else "✗ Not found"}\n", isError = false)
                    appendOutput("wget: ${if (aptManager.isToolAvailable("wget")) "✓ Available" else "✗ Not found"}\n", isError = false)
                }
            } catch (e: Exception) {
                Log.e("PackageManagerActivity", "essential tools error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    isOperationRunning = false
                    setButtonsEnabled(true)
                }
            }
        }.start()
    }

    // ── UI Helpers ──
    private fun appendOutput(text: String, isError: Boolean) {
        val current = outputText.text.toString()
        val color = if (isError) "#FF5555" else "#00FF41"
        outputText.text = current + text
        // Apply simple color coding - red for errors, green for normal
        if (isError) {
            outputText.setTextColor(android.graphics.Color.parseColor(color))
        } else {
            outputText.setTextColor(android.graphics.Color.parseColor(color))
        }
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnUpdate.isEnabled = enabled
        btnUpgrade.isEnabled = enabled
        btnInstall.isEnabled = enabled
        btnSearch.isEnabled = enabled
        btnListInstalled.isEnabled = enabled
        btnEssentialTools.isEnabled = enabled
        progressBar.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    // AptProgressListener implementation
    override fun onStatus(message: String) {
        handler.post {
            statusText.text = message
        }
    }

    override fun onPackageResult(packageName: String, result: String) {
        handler.post {
            appendOutput("$packageName: $result\n", isError = false)
        }
    }

    override fun onComplete(results: Map<String, String>) {
        handler.post {
            isOperationRunning = false
            setButtonsEnabled(true)
            statusText.text = "Package operations complete"
        }
    }

    override fun onError(message: String) {
        handler.post {
            appendOutput("Error: $message\n", isError = true)
            isOperationRunning = false
            setButtonsEnabled(true)
        }
    }
}