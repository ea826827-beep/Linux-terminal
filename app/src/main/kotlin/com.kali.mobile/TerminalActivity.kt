package com.kali.mobile

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Real terminal activity for KALI MOBILE.
 * Provides an actual Linux terminal running through Android's native shell.
 * When Kali Linux is installed, commands are routed through PRoot into the Kali ARM64 userspace.
 *
 * Features:
 * - Real command execution via /system/bin/sh (Android shell)
 * - PRoot integration for Kali Linux environment
 * - Persistent terminal session
 * - Command history (up/down arrows)
 * - Scrollable output
 * - Black background with green/cyan terminal text
 * - Monospace font
 * - Copy/paste support
 * - Clear error handling
 *
 * Does NOT use WebView. Does NOT simulate output.
 * Does NOT claim Kali is installed until it actually is.
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var outputText: TextView
    private lateinit var inputEdit: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var sendButton: ImageButton
    private lateinit var clearButton: ImageButton

    private val shellProcess = ShellProcess()
    private val handler = Handler(Looper.getMainLooper())
    private var isKaliInitialized = false
    private var promptLine = "kali-mobile:~$ "

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        // Initialize views
        outputText = findViewById(R.id.terminalOutput)
        inputEdit = findViewById(R.id.terminalInput)
        scrollView = findViewById(R.id.terminalScroll)
        sendButton = findViewById(R.id.btnSend)
        clearButton = findViewById(R.id.btnClear)

        // Set monospace font and terminal colors
        outputText.typeface = android.graphics.Typeface.MONOSPACE
        inputEdit.typeface = android.graphics.Typeface.MONOSPACE

        // Check if Kali environment is initialized
        val kaliManager = KaliManager(this)
        isKaliInitialized = kaliManager.isEnvironmentInitialized()

        // Start the shell process
        if (!shellProcess.start()) {
            appendOutput("Error: Failed to start terminal session", isError = true)
            return
        }

        // Show welcome banner
        showWelcomeBanner()

        // Send button
        sendButton.setOnClickListener {
            sendCommand()
        }

        // Clear button
        clearButton.setOnClickListener {
            outputText.text = ""
            showWelcomeBanner()
        }

        // Input field - handle Enter key
        inputEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand()
                true
            } else {
                false
            }
        }

        // Handle key events for history navigation (up/down arrows)
        inputEdit.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        val prev = shellProcess.getPreviousHistory()
                        if (prev != null) {
                            inputEdit.setText(prev)
                            inputEdit.setSelection(prev.length)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val next = shellProcess.getNextHistory()
                        if (next != null) {
                            inputEdit.setText(next)
                            inputEdit.setSelection(next.length)
                        } else {
                            inputEdit.setText("")
                        }
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        // Auto-scroll to bottom when text changes
        outputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scrollToBottom()
            }
        })
    }

    /**
     * Shows the welcome banner with terminal info.
     */
    private fun showWelcomeBanner() {
        val banner = StringBuilder()
        banner.append("========================================\n")
        banner.append("  KALI MOBILE - REAL TERMINAL\n")
        banner.append("  Rootless Kali Linux on Android\n")
        banner.append("========================================\n")
        banner.append("\n")
        banner.append("Shell: /system/bin/sh (Android)\n")
        if (isKaliInitialized) {
            banner.append("Kali Linux: INITIALIZED (ARM64 userspace)\n")
            banner.append("Environment: Rootless PRoot userspace\n")
        } else {
            banner.append("Kali Linux: NOT INSTALLED\n")
            banner.append("  Use Dashboard to install Kali Linux ARM64\n")
            banner.append("  Commands run in Android shell until Kali is ready\n")
        }
        banner.append("\n")
        banner.append("Type 'help' for available commands.\n")
        banner.append("Type 'exit' to close terminal.\n")
        banner.append("\n")

        appendOutput(banner.toString(), isError = false)
        appendOutput(promptLine, isError = false, newline = false)
    }

    /**
     * Sends the command from the input field to the shell.
     */
    private fun sendCommand() {
        val command = inputEdit.text.toString().trim()
        if (command.isEmpty()) {
            appendOutput(promptLine, isError = false, newline = false)
            return
        }

        // Clear input
        inputEdit.text.clear()

        // Display the command
        appendOutput(command, isError = false)

        // Handle built-in commands
        when (command.lowercase()) {
            "exit", "quit" -> {
                shellProcess.stop()
                appendOutput("Session ended.", isError = false)
                inputEdit.isEnabled = false
                sendButton.isEnabled = false
                return
            }
            "clear", "reset" -> {
                outputText.text = ""
                showWelcomeBanner()
                return
            }
            "history" -> {
                val hist = shellProcess.getHistory()
                if (hist.isEmpty()) {
                    appendOutput("No command history.", isError = false)
                } else {
                    appendOutput(hist.joinToString("\n") { "$it" }, isError = false)
                }
                appendOutput(promptLine, isError = false, newline = false)
                return
            }
            "kali-status" -> {
                val kaliManager = KaliManager(this)
                val initialized = kaliManager.isEnvironmentInitialized()
                val prootAvailable = kaliManager.isProotAvailable()
                appendOutput("Kali Linux: ${if (initialized) "INSTALLED" else "NOT INSTALLED"}", isError = false)
                appendOutput("Architecture: ARM64", isError = false)
                appendOutput("Environment: ${if (initialized) "Rootless PRoot userspace" else "Android shell only"}", isError = false)
                appendOutput("PRoot binary: ${if (prootAvailable) "Available" else "Not available"}", isError = false)
                appendOutput(promptLine, isError = false, newline = false)
                return
            }
        }

        // Execute the command in a background thread
        val displayCommand = command
        val isKaliCmd = isKaliInitialized

        Thread {
            try {
                val result: String
                if (isKaliCmd) {
                    val kaliManager = KaliManager(this@TerminalActivity)
                    result = shellProcess.executeInKali(displayCommand, kaliManager)
                } else {
                    result = shellProcess.execute(displayCommand)
                }

                handler.post {
                    if (result.isNotEmpty() && result != "(no output)") {
                        appendOutput(result, isError = false)
                    }
                    appendOutput(promptLine, isError = false, newline = false)
                }
            } catch (e: Exception) {
                Log.e("TerminalActivity", "Command execution error", e)
                handler.post {
                    appendOutput("Error: ${e.message}", isError = true)
                    appendOutput(promptLine, isError = false, newline = false)
                }
            }
        }.start()
    }

    /**
     * Appends text to the terminal output.
     */
    @SuppressLint("SetTextI18n")
    private fun appendOutput(text: String, isError: Boolean, newline: Boolean = true) {
        val current = outputText.text.toString()
        val color = if (isError) "#FF5555" else "#00FF41" // green for normal, red for error
        val formatted = if (newline) "$text\n" else text
        outputText.text = current + formatted
        scrollToBottom()
    }

    /**
     * Appends colored text to terminal output (cyan for system messages).
     */
    @SuppressLint("SetTextI18n")
    private fun appendSystemMessage(text: String) {
        val current = outputText.text.toString()
        outputText.text = current + text + "\n"
        scrollToBottom()
    }

    /**
     * Scrolls the ScrollView to the bottom.
     */
    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        shellProcess.stop()
        super.onDestroy()
    }

    override fun onBackPressed() {
        shellProcess.stop()
        super.onBackPressed()
    }
}