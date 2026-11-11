package com.kali.mobile

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Git Integration Activity for KALI MOBILE.
 * Runs real Git commands inside the Kali Linux environment via PRoot.
 *
 * Features:
 * - Initialize Git repositories
 * - Check repository status
 * - Clone public repositories
 * - Commit changes
 * - Run custom Git commands
 *
 * Requires Git to be installed via Package Manager first.
 * Does NOT simulate Git output.
 */
class GitActivity : AppCompatActivity() {

    private lateinit var aptManager: KaliAptManager
    private lateinit var kaliManager: KaliManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scrollView: ScrollView
    private lateinit var outputText: TextView
    private lateinit var repoPathEdit: EditText
    private lateinit var gitCommandEdit: EditText
    private lateinit var btnRun: Button
    private lateinit var btnInit: Button
    private lateinit var btnStatus: Button
    private lateinit var btnClone: Button
    private lateinit var btnCommit: Button
    private lateinit var btnCheckGit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_git)

        kaliManager = KaliManager(this)
        aptManager = KaliAptManager(this, kaliManager)

        // Initialize views
        scrollView = findViewById(R.id.gitScrollView)
        outputText = findViewById(R.id.gitOutputText)
        repoPathEdit = findViewById(R.id.gitRepoPathEdit)
        gitCommandEdit = findViewById(R.id.gitCommandEdit)
        btnRun = findViewById(R.id.btnGitRun)
        btnInit = findViewById(R.id.btnGitInit)
        btnStatus = findViewById(R.id.btnGitStatus)
        btnClone = findViewById(R.id.btnGitClone)
        btnCommit = findViewById(R.id.btnGitCommit)
        btnCheckGit = findViewById(R.id.btnGitCheckGit)
        progressBar = findViewById(R.id.gitProgressBar)
        statusText = findViewById(R.id.gitStatusText)

        // Set monospace font
        outputText.typeface = android.graphics.Typeface.MONOSPACE
        repoPathEdit.typeface = android.graphics.Typeface.MONOSPACE
        gitCommandEdit.typeface = android.graphics.Typeface.MONOSPACE

        // Check Git availability
        checkGitAvailable()

        // Check Git button
        btnCheckGit.setOnClickListener {
            checkGitAvailable()
        }

        // Init repository button
        btnInit.setOnClickListener {
            runGitCommand("init")
        }

        // Status button
        btnStatus.setOnClickListener {
            runGitCommand("status")
        }

        // Clone button
        btnClone.setOnClickListener {
            cloneRepository()
        }

        // Commit button
        btnCommit.setOnClickListener {
            commitChanges()
        }

        // Run custom command button
        btnRun.setOnClickListener {
            val cmd = gitCommandEdit.text.toString().trim()
            if (cmd.isNotEmpty()) {
                runGitCommand(cmd)
            } else {
                Toast.makeText(this, "Enter a git command", Toast.LENGTH_SHORT).show()
            }
        }

        // Auto-scroll output
        outputText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scrollToBottom()
            }
        })
    }

    private fun checkGitAvailable() {
        if (!aptManager.isReady()) {
            statusText.text = "Kali Linux: Not ready"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            appendOutput("Kali Linux environment not initialized. Install from Dashboard first.\n", isError = true)
            setGitButtonsEnabled(false)
            return
        }

        progressBar.visibility = View.VISIBLE
        statusText.text = "Checking Git..."

        Thread {
            val available = aptManager.isToolAvailable("git")
            handler.post {
                progressBar.visibility = View.GONE
                setGitButtonsEnabled(available)
                if (available) {
                    statusText.text = "Git: Available"
                    statusText.setTextColor(ContextCompat.getColor(this@GitActivity, android.R.color.holo_green_light))
                    appendOutput("✓ Git is available in Kali environment\n", isError = false)
                    val version = aptManager.runGitCommand("--version")
                    appendOutput("Version: $version\n", isError = false)
                } else {
                    statusText.text = "Git: Not installed"
                    statusText.setTextColor(ContextCompat.getColor(this@GitActivity, android.R.color.holo_red_light))
                    appendOutput("✗ Git not installed. Install from Package Manager first.\n", isError = true)
                }
            }
        }.start()
    }

    private fun runGitCommand(args: String) {
        if (!aptManager.isToolAvailable("git")) {
            Toast.makeText(this, "Git not installed", Toast.LENGTH_SHORT).show()
            return
        }

        val repoPath = repoPathEdit.text.toString().trim()
        val fullCmd = if (repoPath.isNotEmpty()) "cd '$repoPath' && git $args" else "git $args"

        btnRun.isEnabled = false
        progressBar.visibility = View.VISIBLE
        appendOutput(">>> git $args\n", isError = false)

        Thread {
            try {
                val result = aptManager.runGitCommand(fullCmd)
                handler.post {
                    appendOutput(result + "\n", isError = false)
                    btnRun.isEnabled = true
                    progressBar.visibility = View.GONE
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("GitActivity", "Git execution error", e)
                handler.post {
                    appendOutput("Error: ${e.message}\n", isError = true)
                    btnRun.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun cloneRepository() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Clone Repository")
        builder.setMessage("Enter repository URL:")

        val editText = EditText(this)
        editText.hint = "https://github.com/user/repo.git"
        editText.typeface = android.graphics.Typeface.MONOSPACE
        builder.setView(editText)

        builder.setPositiveButton("Clone") { _, _ ->
            val url = editText.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val repoPath = repoPathEdit.text.toString().trim()
            val cloneCmd = if (repoPath.isNotEmpty()) "cd '$repoPath' && git clone '$url'" else "git clone '$url'"

            btnRun.isEnabled = false
            progressBar.visibility = View.VISIBLE
            appendOutput(">>> git clone $url\n", isError = false)

            Thread {
                try {
                    val result = aptManager.runGitCommand(cloneCmd)
                    handler.post {
                        appendOutput(result + "\n", isError = false)
                        btnRun.isEnabled = true
                        progressBar.visibility = View.GONE
                        scrollToBottom()
                    }
                } catch (e: Exception) {
                    Log.e("GitActivity", "Git clone error", e)
                    handler.post {
                        appendOutput("Error: ${e.message}\n", isError = true)
                        btnRun.isEnabled = true
                        progressBar.visibility = View.GONE
                    }
                }
            }.start()
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun commitChanges() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Git Commit")
        builder.setMessage("Enter commit message:")

        val editText = EditText(this)
        editText.hint = "Initial commit"
        editText.typeface = android.graphics.Typeface.MONOSPACE
        builder.setView(editText)

        builder.setPositiveButton("Commit") { _, _ ->
            val message = editText.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Commit message cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val repoPath = repoPathEdit.text.toString().trim()
            val commitCmd = if (repoPath.isNotEmpty()) "cd '$repoPath' && git add . && git commit -m '$message'" else "git add . && git commit -m '$message'"

            btnRun.isEnabled = false
            progressBar.visibility = View.VISIBLE
            appendOutput(">>> git commit -m '$message'\n", isError = false)

            Thread {
                try {
                    val result = aptManager.runGitCommand(commitCmd)
                    handler.post {
                        appendOutput(result + "\n", isError = false)
                        btnRun.isEnabled = true
                        progressBar.visibility = View.GONE
                        scrollToBottom()
                    }
                } catch (e: Exception) {
                    Log.e("GitActivity", "Git commit error", e)
                    handler.post {
                        appendOutput("Error: ${e.message}\n", isError = true)
                        btnRun.isEnabled = true
                        progressBar.visibility = View.GONE
                    }
                }
            }.start()
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun setGitButtonsEnabled(enabled: Boolean) {
        btnInit.isEnabled = enabled
        btnStatus.isEnabled = enabled
        btnClone.isEnabled = enabled
        btnCommit.isEnabled = enabled
        btnRun.isEnabled = enabled
    }

    private fun appendOutput(text: String, isError: Boolean) {
        val current = outputText.text.toString()
        outputText.text = current + text
        if (isError) {
            outputText.setTextColor(android.graphics.Color.parseColor("#FF5555"))
        } else {
            outputText.setTextColor(android.graphics.Color.parseColor("#00FF41"))
        }
    }

    private fun scrollToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}