package com.kali.mobile

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages APT package operations inside the Kali Linux rootless environment.
 * All commands run through PRoot into the Kali ARM64 userspace.
 *
 * Features:
 * - apt update / upgrade
 * - Install / remove packages
 * - List installed packages
 * - Search packages
 * - Real command output (no simulation)
 */
class KaliAptManager(private val context: Context, private val kaliManager: KaliManager) {

    private val TAG = "KaliAptManager"

    /**
     * Checks if Kali environment is ready for package operations.
     */
    fun isReady(): Boolean {
        return kaliManager.isEnvironmentInitialized() && kaliManager.isProotAvailable()
    }

    /**
     * Runs apt-get update to refresh package lists.
     * Returns the actual command output.
     */
    fun aptUpdate(): String {
        return runAptCommand("apt-get update")
    }

    /**
     * Runs apt-get upgrade to upgrade installed packages.
     * Returns the actual command output.
     */
    fun aptUpgrade(): String {
        return runAptCommand("apt-get upgrade -y")
    }

    /**
     * Installs one or more packages via apt-get.
     * Returns the actual command output.
     */
    fun aptInstall(vararg packages: String): String {
        val pkgList = packages.joinToString(" ")
        return runAptCommand("apt-get install -y $pkgList")
    }

    /**
     * Removes one or more packages via apt-get remove.
     * Returns the actual command output.
     */
    fun aptRemove(vararg packages: String): String {
        val pkgList = packages.joinToString(" ")
        return runAptCommand("apt-get remove -y $pkgList")
    }

    /**
     * Purges one or more packages via apt-get purge.
     * Returns the actual command output.
     */
    fun aptPurge(vararg packages: String): String {
        val pkgList = packages.joinToString(" ")
        return runAptCommand("apt-get purge -y $pkgList")
    }

    /**
     * Lists installed packages via dpkg -l.
     * Returns the actual command output.
     */
    fun listInstalledPackages(): String {
        return runKaliCommand("dpkg -l 2>/dev/null | grep '^ii' | head -100")
    }

    /**
     * Searches for packages via apt-cache search.
     * Returns the actual command output.
     */
    fun searchPackages(query: String): String {
        return runKaliCommand("apt-cache search '$query' 2>/dev/null | head -50")
    }

    /**
     * Shows package information via apt-cache show.
     * Returns the actual command output.
     */
    fun showPackageInfo(packageName: String): String {
        return runKaliCommand("apt-cache show '$packageName' 2>/dev/null | head -40")
    }

    /**
     * Checks if a specific package is installed.
     */
    fun isPackageInstalled(packageName: String): Boolean {
        val output = runKaliCommand("dpkg -s '$packageName' 2>/dev/null | grep 'Status:'")
        return output.contains("install ok installed")
    }

    /**
     * Checks if a specific command/tool is available in Kali.
     */
    fun isToolAvailable(toolName: String): Boolean {
        val output = runKaliCommand("which '$toolName' 2>/dev/null")
        return output.isNotEmpty() && !output.contains("no")
    }

    /**
     * Installs essential development tools: python3, git, nano, vim, curl, wget, openssh-client, build-essential
     */
    fun installEssentialTools(progressListener: AptProgressListener?): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val tools = listOf(
            "python3" to "Python 3",
            "git" to "Git",
            "nano" to "Nano editor",
            "vim" to "Vim editor",
            "curl" to "cURL",
            "wget" to "Wget",
            "openssh-client" to "OpenSSH Client",
            "build-essential" to "Build Essential"
        )

        // First update package lists
        progressListener?.onStatus("Updating package lists...")
        val updateResult = aptUpdate()
        Log.d(TAG, "apt update result: $updateResult")

        for ((pkg, label) in tools) {
            progressListener?.onStatus("Checking $label ($pkg)...")

            if (isPackageInstalled(pkg)) {
                Log.d(TAG, "$pkg already installed")
                results[pkg] = "Already installed"
                continue
            }

            progressListener?.onStatus("Installing $label ($pkg)...")
            val result = aptInstall(pkg)
            Log.d(TAG, "Install $pkg result: $result")

            if (result.contains("E:") || result.contains("error")) {
                results[pkg] = "Failed: $result"
            } else {
                results[pkg] = "Installed"
            }
        }

        progressListener?.onStatus("Package installation complete")
        return results
    }

    /**
     * Runs an apt-get command through PRoot/Kali.
     */
    private fun runAptCommand(command: String): String {
        if (!isReady()) {
            return "Error: Kali Linux environment not initialized. Install Kali first."
        }

        val fullCommand = "$command 2>&1"
        return kaliManager.executeCommand(fullCommand) ?: "Error: Command returned no output"
    }

    /**
     * Runs a general Kali command through PRoot.
     */
    private fun runKaliCommand(command: String): String {
        if (!isReady()) {
            return "Error: Kali Linux environment not initialized."
        }
        return kaliManager.executeCommand("$command 2>&1") ?: "Error: Command returned no output"
    }

    /**
     * Runs a Python script inside Kali.
     */
    fun runPythonScript(scriptPath: String): String {
        if (!isReady()) {
            return "Error: Kali Linux environment not initialized."
        }
        if (!isToolAvailable("python3")) {
            return "Error: Python 3 not installed. Install it from Package Manager first."
        }
        return runKaliCommand("python3 '$scriptPath'")
    }

    /**
     * Runs a Git command inside Kali.
     */
    fun runGitCommand(args: String): String {
        if (!isReady()) {
            return "Error: Kali Linux environment not initialized."
        }
        if (!isToolAvailable("git")) {
            return "Error: Git not installed. Install it from Package Manager first."
        }
        return runKaliCommand("git $args")
    }

    companion object {
        /**
         * List of essential development tools with their package names.
         */
        val ESSENTIAL_TOOLS = listOf(
            "python3" to "Python 3",
            "git" to "Git",
            "nano" to "Nano",
            "vim" to "Vim",
            "curl" to "cURL",
            "wget" to "Wget",
            "openssh-client" to "OpenSSH Client",
            "build-essential" to "Build Essential"
        )
    }
}

/**
 * Listener for APT operation progress.
 */
interface AptProgressListener {
    fun onStatus(message: String)
    fun onPackageResult(packageName: String, result: String)
    fun onComplete(results: Map<String, String>)
    fun onError(message: String)
}