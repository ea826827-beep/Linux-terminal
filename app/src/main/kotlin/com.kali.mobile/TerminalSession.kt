package com.kali.mobile

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

/**
 * Represents a single persistent terminal session.
 * Each session has its own shell process, working directory, and command history.
 */
class TerminalSession(val sessionId: String, var sessionName: String) {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private var isRunning = false
    private var currentDir: String = ""
    private val commandHistory: MutableList<String> = mutableListOf()
    private var historyIndex = -1

    /**
     * Starts the shell process for this session.
     * If Kali is installed, runs through PRoot into Kali userspace.
     * Otherwise, runs Android's native shell.
     */
    fun start(kaliManager: KaliManager?): Boolean {
        stop()

        try {
            val cmdArray = if (kaliManager != null && kaliManager.isEnvironmentInitialized() && kaliManager.isProotAvailable()) {
                // Run through PRoot into Kali ARM64 userspace
                val rootPath = kaliManager.getKaliRootPath()
                val prootPath = kaliManager.getProotPath()
                arrayOf(
                    prootPath, "-0", "-r", rootPath,
                    "-b", "/dev", "-b", "/proc", "-b", "/sys",
                    "-b", kaliManager.getKaliHomeDir(),
                    "-w", rootPath,
                    "/bin/bash"
                )
            } else {
                // Fall back to Android native shell
                arrayOf("/system/bin/sh")
            }

            process = java.lang.Runtime.getRuntime().exec(cmdArray)
            writer = PrintWriter(process!!.outputStream, true)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
            isRunning = true

            // Read error stream in background to avoid buffer blocking
            Thread {
                try {
                    errorReader!!.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.e("TerminalSession", "Session $sessionId stderr: $line")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TerminalSession", "Error reader closed for session $sessionId")
                }
            }.start()

            // Read initial output (banner, prompt)
            Thread {
                try {
                    Thread.sleep(500)
                    reader!!.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.d("TerminalSession", "Session $sessionId init: $line")
                        }
                    }
                } catch (e: Exception) {
                    // Expected when process ends
                }
            }.start()

            Log.d("TerminalSession", "Session $sessionId started")
            return true
        } catch (e: Exception) {
            Log.e("TerminalSession", "Failed to start session $sessionId", e)
            isRunning = false
            return false
        }
    }

    /**
     * Executes a command in this session and returns the output.
     */
    fun execute(command: String): String {
        if (!isRunning || writer == null || reader == null) {
            return "Error: Session not running"
        }

        val trimmed = command.trim()
        if (trimmed.isNotEmpty()) {
            if (commandHistory.isEmpty() || commandHistory.last() != trimmed) {
                commandHistory.add(trimmed)
            }
            if (commandHistory.size > 500) {
                commandHistory.removeAt(0)
            }
        }

        try {
            writer!!.println(command)
            writer!!.flush()

            val output = StringBuilder()
            val startTime = System.currentTimeMillis()
            val timeout = 15000

            while (System.currentTimeMillis() - startTime < timeout) {
                if (reader!!.ready()) {
                    val line = reader!!.readLine()
                    if (line == null) break
                    output.append(line).append("\n")
                } else {
                    Thread.sleep(50)
                    if (!reader!!.ready() && output.isNotEmpty()) {
                        Thread.sleep(100)
                        if (!reader!!.ready()) break
                    }
                }
            }

            val result = output.toString().trim()
            return if (result.isEmpty()) "(no output)" else result
        } catch (e: Exception) {
            Log.e("TerminalSession", "Error executing command in session $sessionId", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Changes the working directory for this session.
     */
    fun changeDirectory(path: String): String {
        return execute("cd '$path' && pwd")
    }

    /**
     * Gets the command history for this session.
     */
    fun getHistory(): List<String> = commandHistory.toList()

    /**
     * Gets previous command in history (up arrow).
     */
    fun getPreviousHistory(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return commandHistory[commandHistory.size - 1 - historyIndex]
    }

    /**
     * Gets next command in history (down arrow).
     */
    fun getNextHistory(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size - 1)
        return if (historyIndex == commandHistory.size - 1) null
        else commandHistory[commandHistory.size - 1 - historyIndex]
    }

    /**
     * Clears command history.
     */
    fun clearHistory() {
        commandHistory.clear()
        historyIndex = -1
    }

    /**
     * Stops this session's shell process.
     */
    fun stop() {
        isRunning = false
        try {
            writer?.close()
            reader?.close()
            errorReader?.close()
            process?.destroy()
            process?.waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("TerminalSession", "Error stopping session $sessionId", e)
        } finally {
            writer = null
            reader = null
            errorReader = null
            process = null
        }
    }

    fun isSessionRunning(): Boolean = isRunning && process?.isAlive == true
}

/**
 * Manages multiple terminal sessions.
 * Provides session creation, switching, and lifecycle management.
 */
class TerminalSessionManager {

    private val sessions: MutableMap<String, TerminalSession> = mutableMapOf()
    private var currentSessionId: String? = null
    private val kaliManager: KaliManager?

    constructor(kaliManager: KaliManager?) {
        this.kaliManager = kaliManager
    }

    /**
     * Creates a new terminal session.
     */
    fun createSession(sessionName: String = "Session"): TerminalSession {
        val sessionId = "session_${System.currentTimeMillis()}"
        val session = TerminalSession(sessionId, sessionName)
        sessions[sessionId] = session

        // Start the session
        session.start(kaliManager)

        // Set as current if it's the first one
        if (currentSessionId == null) {
            currentSessionId = sessionId
        }

        Log.d("TerminalSessionManager", "Created session $sessionId ($sessionName)")
        return session
    }

    /**
     * Gets the current active session.
     */
    fun getCurrentSession(): TerminalSession? {
        return if (currentSessionId != null) sessions[currentSessionId] else null
    }

    /**
     * Switches to a different session by ID.
     */
    fun switchTo(sessionId: String): Boolean {
        if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            Log.d("TerminalSessionManager", "Switched to session $sessionId")
            return true
        }
        return false
    }

    /**
     * Closes a session and removes it.
     */
    fun closeSession(sessionId: String): Boolean {
        val session = sessions.remove(sessionId)
        session?.stop()

        if (currentSessionId == sessionId) {
            currentSessionId = null
            // Switch to another session if available
            if (sessions.isNotEmpty()) {
                currentSessionId = sessions.keys.first()
            }
        }
        return session != null
    }

    /**
     * Renames a session.
     */
    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = sessions[sessionId]
        if (session != null) {
            session.sessionName = newName
            return true
        }
        return false
    }

    /**
     * Gets all sessions.
     */
    fun getAllSessions(): List<TerminalSession> = sessions.values.toList()

    /**
     * Gets the current session ID.
     */
    fun getCurrentSessionId(): String? = currentSessionId

    /**
     * Stops all sessions.
     */
    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
        currentSessionId = null
    }

    /**
     * Gets session count.
     */
    fun getSessionCount(): Int = sessions.size
}