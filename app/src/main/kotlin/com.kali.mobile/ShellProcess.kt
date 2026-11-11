package com.kali.mobile

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.TimeUnit

/**
 * Manages a persistent shell process for executing real Linux commands.
 * Keeps the process alive across multiple command executions for session persistence.
 *
 * This runs commands through Android's native shell (/system/bin/sh).
 * When Kali Linux is installed, commands will be routed through PRoot into the Kali environment.
 */
class ShellProcess {

    private var process: Process? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var errorReader: BufferedReader? = null
    private var isRunning = false
    private val commandHistory: MutableList<String> = mutableListOf()
    private var historyIndex = -1

    /**
     * Starts a new shell process. If one is already running, it is destroyed first.
     */
    fun start(): Boolean {
        stop()

        try {
            // Use Android's native shell - this is a real shell, not simulated
            process = Runtime.getRuntime().exec("/system/bin/sh")
            writer = PrintWriter(process!!.outputStream, true)
            reader = BufferedReader(InputStreamReader(process!!.inputStream))
            errorReader = BufferedReader(InputStreamReader(process!!.errorStream))
            isRunning = true

            Log.d("ShellProcess", "Shell process started (PID: ${process!!.hashCode()})")

            // Start error stream reader to avoid buffer blocking
            Thread {
                errorReader!!.useLines { lines ->
                    lines.forEach { line ->
                        Log.e("ShellProcess", "Shell stderr: $line")
                    }
                }
            }.start()

            return true
        } catch (e: Exception) {
            Log.e("ShellProcess", "Failed to start shell process", e)
            isRunning = false
            return false
        }
    }

    /**
     * Executes a command in the persistent shell and returns the output.
     * Commands are executed sequentially - each call waits for completion.
     */
    fun execute(command: String): String {
        if (!isRunning || writer == null || reader == null) {
            if (!start()) {
                return "Error: Unable to start shell process"
            }
        }

        // Add to command history (avoid duplicates and empty commands)
        val trimmed = command.trim()
        if (trimmed.isNotEmpty()) {
            if (commandHistory.isEmpty() || commandHistory.last() != trimmed) {
                commandHistory.add(trimmed)
            }
            // Limit history size
            if (commandHistory.size > 500) {
                commandHistory.removeAt(0)
            }
        }

        try {
            // Send command to shell
            writer!!.println(command)
            writer!!.flush()

            // Read output until we see a prompt or the command completes
            val output = StringBuilder()
            val startTime = System.currentTimeMillis()
            val timeout = 15000 // 15 second timeout

            while (System.currentTimeMillis() - startTime < timeout) {
                if (reader!!.ready()) {
                    val line = reader!!.readLine()
                    if (line == null) {
                        // Process may have ended
                        break
                    }
                    output.append(line).append("\n")
                } else {
                    // Small sleep to avoid busy-waiting
                    Thread.sleep(50)
                    // Check if there's more data coming
                    if (!reader!!.ready() && output.isNotEmpty()) {
                        // Brief pause to let output complete
                        Thread.sleep(100)
                        if (!reader!!.ready()) {
                            break
                        }
                    }
                }
            }

            val result = output.toString().trim()
            return if (result.isEmpty()) "(no output)" else result

        } catch (e: Exception) {
            Log.e("ShellProcess", "Error executing command: $command", e)
            return "Error: ${e.message}"
        }
    }

    /**
     * Executes a command in the Kali Linux environment via PRoot.
     * Delegates to KaliManager.executeCommand which uses the full PRoot binary path.
     */
    fun executeInKali(command: String, kaliManager: KaliManager): String {
        if (!kaliManager.isEnvironmentInitialized()) {
            return "Kali Linux environment not initialized. Please install from Dashboard."
        }

        if (!kaliManager.isProotAvailable()) {
            return "PRoot binary not available. Please reinstall Kali Linux."
        }

        // Use KaliManager's executeCommand which uses the full PRoot binary path
        return kaliManager.executeCommand(command) ?: "Error: Command returned no output"
    }

    /**
     * Gets the command history.
     */
    fun getHistory(): List<String> = commandHistory.toList()

    /**
     * Gets the previous command in history (for up-arrow navigation).
     */
    fun getPreviousHistory(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = (historyIndex - 1).coerceAtLeast(0)
        return commandHistory[commandHistory.size - 1 - historyIndex]
    }

    /**
     * Gets the next command in history (for down-arrow navigation).
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
     * Stops the shell process.
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
            Log.w("ShellProcess", "Error stopping shell process", e)
        } finally {
            writer = null
            reader = null
            errorReader = null
            process = null
        }
    }

    /**
     * Checks if the shell process is currently running.
     */
    fun isShellRunning(): Boolean = isRunning && process?.isAlive == true
}