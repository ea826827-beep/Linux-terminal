package com.kali.mobile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Multi-session terminal activity for KALI MOBILE.
 * Supports multiple persistent terminal tabs with independent working directories.
 * Each session runs real Linux processes (Android shell or Kali via PRoot).
 *
 * Features:
 * - Multiple terminal sessions (tabs)
 * - Create, close, rename sessions
 * - Session switching via dropdown
 * - Independent command history per session
 * - Real command output (no simulation)
 * - Black background with green/cyan terminal text
 * - Monospace font, scrollable output
 * - Persistent sessions while app is active
 */
class MultiSessionTerminalActivity : AppCompatActivity() {

    private lateinit var outputText: TextView
    private lateinit var inputEdit: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var sendButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var sessionSpinner: Spinner
    private lateinit var newSessionButton: ImageButton
    private lateinit var closeSessionButton: ImageButton
    private lateinit var renameSessionButton: ImageButton

    private val sessionManager: TerminalSessionManager by lazy {
        TerminalSessionManager(KaliManager(this@MultiSessionTerminalActivity))
    }
    private val handler = Handler(Looper.getMainLooper())
    private var promptLine = "kali-mobile:~$ "

    // Session adapter for the spinner
    private lateinit var sessionAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_session_terminal)

        // Initialize views
        outputText = findViewById(R.id.terminalOutput)
        inputEdit = findViewById(R.id.terminalInput)
        scrollView = findViewById(R.id.terminalScroll)
        sendButton = findViewById(R.id.btnSend)
        clearButton = findViewById(R.id.btnClear)
        sessionSpinner = findViewById(R.id.sessionSpinner)
        newSessionButton = findViewById(R.id.btnNewSession)
        closeSessionButton = findViewById(R.id.btnCloseSession)
        renameSessionButton = findViewById(R.id.btnRenameSession)

        // Set monospace font and terminal colors
        outputText.typeface = android.graphics.Typeface.MONOSPACE
        inputEdit.typeface = android.graphics.Typeface.MONOSPACE

        // Initialize session adapter
        sessionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emptyList<String>())
        sessionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionSpinner.adapter = sessionAdapter

        // Create the first session
        createNewSession()

        // Session spinner selection
        sessionSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val sessionId = sessionManager.getAllSessions()[position].sessionId
                sessionManager.switchTo(sessionId)
                refreshDisplayForCurrentSession()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // New session button
        newSessionButton.setOnClickListener {
            createNewSession()
        }

        // Close session button
        closeSessionButton.setOnClickListener {
            closeCurrentSession()
        }

        // Rename session button
        renameSessionButton.setOnClickListener {
            renameCurrentSession()
        }

        // Send button
        sendButton.setOnClickListener {
            sendCommand()
        }

        // Clear button
        clearButton.setOnClickListener {
            outputText.text = ""
            showWelcomeBannerForCurrentSession()
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
                        val prev = sessionManager.getCurrentSession()?.getPreviousHistory()
                        if (prev != null) {
                            inputEdit.setText(prev)
                            inputEdit.setSelection(prev.length)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val next = sessionManager.getCurrentSession()?.getNextHistory()
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
     * Creates a new terminal session and makes it current.
     */
    private fun createNewSession() {
        val session = sessionManager.createSession("Session ${sessionManager.getSessionCount() + 1}")
        updateSessionSpinner()
        sessionSpinner.setSelection(sessionAdapter.count - 1)
        showWelcomeBannerForCurrentSession()
    }

    /**
     * Closes the current session.
     */
    private fun closeCurrentSession() {
        val current = sessionManager.getCurrentSession()
        if (current == null) return

        val sessionCount = sessionManager.getSessionCount()
        if (sessionCount <= 1) {
            // Don't close the last session - just clear output
            outputText.text = ""
            showWelcomeBannerForCurrentSession()
            return
        }

        val sessionId = current.sessionId
        sessionManager.closeSession(sessionId)
        updateSessionSpinner()

        // Select the first available session
        sessionSpinner.setSelection(0)
        showWelcomeBannerForCurrentSession()
    }

    /**
     * Renames the current session.
     */
    private fun renameCurrentSession() {
        val current = sessionManager.getCurrentSession()
        if (current == null) return

        val input = EditText(this)
        input.setText(current.sessionName)
        input.setSelection(current.sessionName.length)

        AlertDialog.Builder(this)
            .setTitle("Rename Session")
            .setMessage("Enter new session name:")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    sessionManager.renameSession(current.sessionId, newName)
                    updateSessionSpinner()
                    sessionSpinner.setSelection(
                        sessionManager.getAllSessions().indexOfFirst { it.sessionId == current.sessionId }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Updates the session spinner with current sessions.
     */
    private fun updateSessionSpinner() {
        val sessions = sessionManager.getAllSessions()
        val names = sessions.map { it.sessionName }
        sessionAdapter.clear()
        sessionAdapter.addAll(names)
        sessionAdapter.notifyDataSetChanged()
    }

    /**
     * Refreshes the display for the current session.
     */
    private fun refreshDisplayForCurrentSession() {
        val session = sessionManager.getCurrentSession()
        if (session == null) return

        outputText.text = ""
        showWelcomeBannerForCurrentSession()
    }

    /**
     * Shows the welcome banner for the current session.
     */
    private fun showWelcomeBannerForCurrentSession() {
        val session = sessionManager.getCurrentSession()
        val sessionName = session?.sessionName ?: "Unknown"
        val isKali = KaliManager(this).isEnvironmentInitialized()

        val banner = StringBuilder()
        banner.append("========================================\n")
        banner.append("  KALI MOBILE - TERMINAL SESSION\n")
        banner.append("  Name: $sessionName\n")
        banner.append("========================================\n")
        banner.append("\n")
        banner.append("Shell: ${if (isKali) "/bin/bash (Kali ARM64 via PRoot)" else "/system/bin/sh (Android)"}\n")
        banner.append("Kali Linux: ${if (isKali) "INITIALIZED" else "NOT INSTALLED"}\n")
        banner.append("\n")
        banner.append("Type 'help' for available commands.\n")
        banner.append("Type 'exit' to close this session.\n")
        banner.append("\n")

        appendOutput(banner.toString(), isError = false)
        appendOutput(promptLine, isError = false, newline = false)
    }

    /**
     * Sends the command from the input field to the current session.
     */
    private fun sendCommand() {
        val session = sessionManager.getCurrentSession()
        if (session == null) {
            appendOutput("Error: No active session", isError = true)
            return
        }

        val command = inputEdit.text.toString().trim()
        if (command.isEmpty()) {
            appendOutput(promptLine, isError = false, newline = false)
            return
        }

        inputEdit.text.clear()
        appendOutput(command, isError = false)

        // Handle built-in commands
        when (command.lowercase()) {
            "exit", "quit" -> {
                closeCurrentSession()
                return
            }
            "clear", "reset" -> {
                outputText.text = ""
                showWelcomeBannerForCurrentSession()
                return
            }
            "history" -> {
                val hist = session.getHistory()
                if (hist.isEmpty()) {
                    appendOutput("No command history.", isError = false)
                } else {
                    appendOutput(hist.joinToString("\n") { it }, isError = false)
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
        Thread {
            try {
                val result = session.execute(displayCommand)
                handler.post {
                    if (result.isNotEmpty() && result != "(no output)") {
                        appendOutput(result, isError = false)
                    }
                    appendOutput(promptLine, isError = false, newline = false)
                }
            } catch (e: Exception) {
                Log.e("MultiSessionTerminal", "Command execution error", e)
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
        val formatted = if (newline) "$text\n" else text
        outputText.text = current + formatted
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
        sessionManager.stopAll()
        super.onDestroy()
    }

    override fun onBackPressed() {
        sessionManager.stopAll()
        super.onBackPressed()
    }
}