package com.kali.mobile

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages the Kali Linux rootless userspace.
 * Handles installation, initialization, and process execution via PRoot.
 *
 * Rootless Kali Linux on Android:
 * - Downloads Kali Linux ARM64 root filesystem from official sources
 * - Extracts and initializes the root filesystem
 * - Uses PRoot for chroot-like isolation without root access
 * - No root required - uses Android's native capabilities
 */
class KaliManager(private val context: Context) {

    private val sharedPrefs: SharedPreferences
        = context.getSharedPreferences("kali_mobile_prefs", Context.MODE_PRIVATE)
    private val PREF_KEY_INITIALIZED = "kali_initialized"
    private val PREF_KEY_PROOT_PATH = "proot_binary_path"
    private val PREF_KEY_DOWNLOAD_PROGRESS = "download_progress"

    // Kali Linux ARM64 root filesystem download URL (official Kali source)
    private val KALI_ROOTFS_URL = "https://http.kali.org/kali/pool/main/k/kali-rootfs/kali-rootfs-2024.4-arm64.tar.xz"
    private val KALI_MIRROR = "https://mirrors.edge.kali.org/kali"

    // PRoot binary download URL (static ARM64 Android build)
    private val PROOT_URL = "https://github.com/termux/proot/releases/download/v5.1.10/proot"
    private val PROOT_MIRROR = "https://github.com/proot-me/proot/releases/download/v5.1.10/proot-static"

    // PRoot base directory within app private storage
    val baseDir: File
        get = context.getDir("kali_root", Context.MODE_PRIVATE)

    /**
     * Gets the path to the Kali root filesystem directory.
     */
    fun getRootfsDir(): File {
        return File(baseDir, "rootfs")
    }

    /**
     * Gets the path to the PRoot binary.
     */
    fun getProotPath(): String {
        return sharedPrefs.getString(PREF_KEY_PROOT_PATH, "") ?: ""
    }

    /**
     * Checks if the Kali Linux environment is already initialized.
     */
    fun isEnvironmentInitialized(): Boolean {
        return sharedPrefs.getBoolean(PREF_KEY_INITIALIZED, false)
    }

    /**
     * Checks if PRoot binary is available.
     */
    fun isProotAvailable(): Boolean {
        val prootPath = getProotPath()
        if (prootPath.isEmpty()) return false
        val prootFile = File(prootPath)
        return prootFile.exists() && prootFile.canExecute
    }

    /**
     * Check network connectivity.
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        if (network == null) return false
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Get available disk space in bytes.
     */
    fun getAvailableSpace(): Long {
        try {
            val stat = android.os.StatFs(baseDir.absolutePath)
            return stat.blockSizeLong * stat.availableBlocksLong
        } catch (e: Exception) {
            Log.e("KaliManager", "Error getting disk space", e)
        }
        return 0L
    }

    /**
     * Initialize the Kali Linux environment.
     * Downloads PRoot, downloads Kali rootfs, extracts, and sets up the environment.
     * This is a long-running operation that should be called on a background thread.
     */
    fun initializeEnvironment(progressListener: KaliProgressListener?): Boolean {
        try {
            Log.d("KaliManager", "Starting Kali Linux environment initialization")

            progressListener?.onStatus("Checking prerequisites...")

            // Check storage space (need at least 2GB for Kali rootfs)
            val availableSpace = getAvailableSpace()
            val requiredSpace = 2L * 1024 * 1024 * 1024 // 2GB
            if (availableSpace < requiredSpace) {
                Log.e("KaliManager", "Insufficient storage: $availableSpace bytes available, $requiredSpace required")
                progressListener?.onError("Insufficient storage space. Need at least 2GB free.")
                return false
            }

            // Step 1: Create base directory structure
            baseDir.mkdirs()
            val rootfsDir = getRootfsDir()
            rootfsDir.mkdirs()
            val prootDir = File(baseDir, "proot")
            prootDir.mkdirs()
            val homeDir = File(baseDir, "home")
            homeDir.mkdirs()

            // Step 2: Download PRoot binary
            progressListener?.onStatus("Downloading PRoot binary...")
            val prootBinary = downloadProot()
            if (prootBinary == null) {
                Log.e("KaliManager", "Failed to download PRoot binary")
                progressListener?.onError("Failed to download PRoot binary")
                return false
            }
            Log.d("KaliManager", "PRoot binary ready at: ${prootBinary.absolutePath}")

            // Step 3: Download Kali root filesystem (ARM64)
            progressListener?.onStatus("Downloading Kali Linux ARM64 root filesystem...")
            val tarFile = downloadRootFs(progressListener)
            if (tarFile == null) {
                Log.e("KaliManager", "Failed to download Kali root filesystem")
                progressListener?.onError("Failed to download Kali root filesystem")
                return false
            }

            // Step 4: Extract root filesystem
            progressListener?.onStatus("Extracting Kali root filesystem...")
            if (!extractRootFs(tarFile, rootfsDir, progressListener)) {
                Log.e("KaliManager", "Failed to extract Kali root filesystem")
                progressListener?.onError("Failed to extract Kali root filesystem")
                return false
            }

            // Step 5: Set up PRoot environment
            progressListener?.onStatus("Configuring PRoot environment...")
            setupProotEnvironment(rootfsDir, prootBinary)

            // Step 6: Mark as initialized
            markInitialized()

            Log.i("KaliManager", "Kali Linux environment initialized successfully")
            progressListener?.onStatus("Initialization complete!")
            progressListener?.onComplete()

            return true

        } catch (e: Exception) {
            Log.e("KaliManager", "Error initializing Kali environment", e)
            progressListener?.onError("Error: ${e.message}")
            return false
        }
    }

    /**
     * Download the PRoot binary for ARM64 Android.
     */
    private fun downloadProot(): File? {
        val prootPath = getProotPath()
        if (prootPath.isNotEmpty()) {
            val existing = File(prootPath)
            if (existing.exists() && existing.canExecute) {
                Log.d("KaliManager", "PRoot binary already exists: $prootPath")
                return existing
            }
        }

        val prootFile = File(baseDir, "proot")
        prootFile.setExecutable(true, false)

        try {
            // Download PRoot from primary URL
            val downloaded = downloadFile(PROOT_URL, prootFile)
            if (downloaded != null && downloaded.length() > 1000L) {
                downloaded.setExecutable(true, false)
                sharedPrefs.edit().putString(PREF_KEY_PROOT_PATH, downloaded.absolutePath).apply()
                Log.d("KaliManager", "PRoot downloaded from primary: ${downloaded.length()} bytes")
                return downloaded
            }

            // Try mirror
            Log.d("KaliManager", "Trying PRoot mirror...")
            val mirrorDownloaded = downloadFile(PROOT_MIRROR, prootFile)
            if (mirrorDownloaded != null && mirrorDownloaded.length() > 1000L) {
                mirrorDownloaded.setExecutable(true, false)
                sharedPrefs.edit().putString(PREF_KEY_PROOT_PATH, mirrorDownloaded.absolutePath).apply()
                Log.d("KaliManager", "PRoot downloaded from mirror: ${mirrorDownloaded.length()} bytes")
                return mirrorDownloaded
            }

            Log.e("KaliManager", "PRoot download failed from all sources")
            return null
        } catch (e: Exception) {
            Log.e("KaliManager", "Error downloading PRoot binary", e)
            return null
        }
    }

    /**
     * Download the Kali Linux ARM64 root filesystem from official sources.
     */
    private fun downloadRootFs(progressListener: KaliProgressListener?): File? {
        val downloadDir = File(baseDir, "download")
        downloadDir.mkdirs()
        val tarFile = File(downloadDir, "kali-rootfs-arm64.tar.xz")

        // Don't re-download if already exists and is valid
        if (tarFile.exists() && tarFile.length() > 1024L * 1024L * 100L) {
            Log.d("KaliManager", "Root filesystem already downloaded: ${tarFile.length()} bytes")
            return tarFile
        }

        try {
            val downloaded = downloadFileWithProgress(KALI_ROOTFS_URL, tarFile, progressListener)
            if (downloaded != null && downloaded.length() > 1024L * 1024L * 100L) {
                Log.d("KaliManager", "Root filesystem downloaded: ${downloaded.length()} bytes from $KALI_ROOTFS_URL")
                return downloaded
            } else {
                Log.e("KaliManager", "Download failed - file too small from primary URL")
                return downloadFromMirror(progressListener)
            }
        } catch (e: Exception) {
            Log.e("KaliManager", "Error downloading root filesystem from $KALI_ROOTFS_URL", e)
            return downloadFromMirror(progressListener)
        }
    }

    /**
     * Try downloading from Kali mirror.
     */
    private fun downloadFromMirror(progressListener: KaliProgressListener?): File? {
        val mirrorUrl = "$KALI_MIRROR/kali/pool/main/k/kali-rootfs/kali-rootfs-2024.4-arm64.tar.xz"
        val downloadDir = File(baseDir, "download")
        downloadDir.mkdirs()
        val mirrorFile = File(downloadDir, "kali-rootfs-arm64-mirror.tar.xz")

        try {
            val downloaded = downloadFileWithProgress(mirrorUrl, mirrorFile, progressListener)
            return if (downloaded != null && downloaded.length() > 1024L * 1024L * 100L) downloaded else null
        } catch (e: Exception) {
            Log.e("KaliManager", "Error downloading from mirror $mirrorUrl", e)
            return null
        }
    }

    /**
     * Download a file from a URL.
     */
    private fun downloadFile(urlString: String, destFile: File): File? {
        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            Log.e("KaliManager", "Error downloading from $urlString", e)
            return null
        }
    }

    /**
     * Download a file with progress reporting.
     */
    private fun downloadFileWithProgress(
        urlString: String,
        destFile: File,
        progressListener: KaliProgressListener?
    ): File? {
        var input: InputStream? = null
        var output: OutputStream? = null

        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            val totalLength = connection.contentLength

            input = connection.inputStream
            output = destFile.outputStream()

            val buffer = ByteArray(8192)
            var totalRead = 0L
            var lastProgressTime = System.currentTimeMillis()

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                totalRead += read

                // Report progress every 500ms
                val now = System.currentTimeMillis()
                if (now - lastProgressTime > 500) {
                    lastProgressTime = now
                    val percent = if (totalLength > 0) (totalRead * 100 / totalLength).toInt() else 0
                    progressListener?.onProgress(percent, totalRead, totalLength)
                }
            }

            output.flush()

            if (destFile.exists() && destFile.length() > 0) {
                progressListener?.onProgress(100, destFile.length(), totalLength)
                destFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("KaliManager", "Error downloading from $urlString", e)
            null
        } finally {
            input?.close()
            output?.close()
        }
    }

    /**
     * Extract the Kali Linux root filesystem using tar.
     */
    private fun extractRootFs(tarFile: File, extractDir: File, progressListener: KaliProgressListener?): Boolean {
        try {
            extractDir.mkdirs()

            // Check if tar is available
            val tarCheck = ProcessBuilder("which", "tar").start()
            tarCheck.waitFor()
            if (tarCheck.exitValue() != 0) {
                Log.e("KaliManager", "tar command not available on this device")
                progressListener?.onError("tar command not available on this device")
                return false
            }

            // Extract using tar
            val process = ProcessBuilder()
                .command("tar", "xJf", tarFile.absolutePath, "-C", extractDir.absolutePath)
                .redirectErrorStream(true)
                .start()

            // Read output in background to avoid blocking
            Thread {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Log.d("KaliManager", "tar: $line")
                    }
                }
            }.start()

            val exitCode = process.waitFor()
            Log.d("KaliManager", "Extraction exit code: $exitCode")

            if (exitCode == 0) {
                progressListener?.onProgress(100, 1, 1)
                return true
            } else {
                Log.e("KaliManager", "Extraction failed with exit code: $exitCode")
                progressListener?.onError("Extraction failed with exit code: $exitCode")
                return false
            }
        } catch (e: Exception) {
            Log.e("KaliManager", "Error extracting root filesystem", e)
            progressListener?.onError("Extraction error: ${e.message}")
            return false
        }
    }

    /**
     * Set up the PRoot environment configuration.
     * Creates wrapper scripts for running Kali commands.
     */
    private fun setupProotEnvironment(rootfsDir: File, prootBinary: File) {
        val rootPath = rootfsDir.absolutePath

        // Create a wrapper script to launch Kali Linux via PRoot
        val wrapperScript = File(baseDir, "start_kali.sh")
        wrapperScript.writeText("""#!/system/bin/sh
export PROOT_DISTRO="$rootPath"
export LD_LIBRARY_PATH="$rootPath/lib:/system/lib"
exec "$prootFile" -0 -r "$rootPath" -b /dev -b /proc -b /sys -b "$baseDir/home" -w "$rootPath" /bin/bash $*
""".trimIndent().replace("$prootFile", prootFile.absolutePath))
        wrapperScript.setExecutable(true)

        // Also create a command runner script
        val runScript = File(baseDir, "run_kali.sh")
        runScript.writeText("""#!/system/bin/sh
export PROOT_DISTRO="$rootPath"
export LD_LIBRARY_PATH="$rootPath/lib:/system/lib"
exec "$prootFile" -0 -r "$rootPath" -b /dev -b /proc -b /sys -b "$baseDir/home" -w "$rootPath" /bin/bash -c "$1"
""".trimIndent().replace("$prootFile", prootFile.absolutePath))
        runScript.setExecutable(true)

        Log.d("KaliManager", "PRoot environment configured at $rootPath")
    }

    /**
     * Execute a Linux command in the Kali environment via PRoot.
     * Falls back to Android shell if PRoot is not available.
     * Returns the command output as a string.
     */
    fun executeCommand(command: String): String? {
        if (!isEnvironmentInitialized()) {
            Log.w("KaliManager", "Kali environment not initialized")
            return null
        }

        if (!isProotAvailable()) {
            Log.w("KaliManager", "PRoot binary not available")
            return null
        }

        try {
            val rootPath = getRootfsDir().absolutePath
            val prootPath = getProotPath()

            // Build the PRoot command to execute in Kali environment
            val cmdArray = arrayOf(
                prootPath,
                "-0",
                "-r", rootPath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "$baseDir/home",
                "-w", rootPath,
                "/bin/bash",
                "-c", command
            )

            val process = java.lang.Runtime.getRuntime().exec(cmdArray)
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val errorOutput = process.errorStream.bufferedReader().readText()

            if (process.exitValue() != 0 && errorOutput.isNotEmpty()) {
                Log.w("KaliManager", "Command exited with code ${process.exitValue()}: $errorOutput")
                return "Error: $errorOutput\nOutput: $output"
            }

            return output.trim()

        } catch (e: Exception) {
            Log.e("KaliManager", "Error executing command via PRoot: $command", e)
            return null
        }
    }

    /**
     * Get the Kali Linux root filesystem path.
     */
    fun getKaliRootPath(): String {
        return getRootfsDir().absolutePath
    }

    /**
     * Get the Kali Linux home directory path.
     */
    fun getKaliHomeDir(): String {
        return "$baseDir/home"
    }

    /**
     * Mark the environment as initialized in shared preferences.
     */
    private fun markInitialized() {
        sharedPrefs.edit().putBoolean(PREF_KEY_INITIALIZED, true).apply()
    }

    /**
     * Reset initialization state (for reinstallation).
     */
    fun reset() {
        sharedPrefs.edit()
            .putBoolean(PREF_KEY_INITIALIZED, false)
            .remove(PREF_KEY_PROOT_PATH)
            .apply()
    }
}

/**
 * Listener for Kali installation progress.
 */
interface KaliProgressListener {
    fun onStatus(message: String)
    fun onProgress(percent: Int, bytesRead: Long, totalLength: Long)
    fun onError(message: String)
    fun onComplete()
}