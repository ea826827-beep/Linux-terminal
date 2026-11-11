package com.kali.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

/**
 * File manager for browsing and editing files in the Kali Linux filesystem.
 * Provides real file operations: browse, create, rename, delete, read, edit.
 *
 * All operations are performed on the real filesystem (app private storage
 * or Kali rootfs via PRoot when initialized).
 *
 * Does NOT simulate file operations. All changes are real.
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var fileAdapter: FileAdapter
    private lateinit var kaliManager: KaliManager
    private lateinit var pathText: TextView
    private var currentDir: File = File(filesDir, "kali_home")

    // Request codes for dialog results
    private val RC_CREATE_FOLDER = 1001
    private val RC_CREATE_FILE = 1002
    private val RC_RENAME = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView)
        toolbar = findViewById(R.id.toolbar)
        pathText = findViewById(R.id.pathText)

        setSupportActionBar(toolbar)
        supportActionBar?.setTitle("KALI FILE MANAGER")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize Kali manager
        kaliManager = KaliManager(this)

        // Set starting directory - use Kali home if initialized, otherwise app home
        if (kaliManager.isEnvironmentInitialized()) {
            currentDir = File(kaliManager.getKaliHomeDir())
        } else {
            currentDir = File(filesDir, "kali_home")
            currentDir.mkdirs()
        }

        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        fileAdapter = FileAdapter(this) { file ->
            onFileSelected(file)
        }
        recyclerView.adapter = fileAdapter

        // Load initial directory
        loadDirectory(currentDir)
    }

    private fun loadDirectory(dir: File) {
        currentDir = dir
        val files = dir.listFiles()?.filter { it.isFile || it.isDirectory }?.sortedBy { !it.isDirectory } ?: emptyList()
        fileAdapter.submitFiles(files)

        // Update action bar title and path text
        supportActionBar?.setTitle(" ${dir.absolutePath}")
        pathText.text = dir.absolutePath
    }

    private fun onFileSelected(file: File) {
        if (file.isDirectory) {
            loadDirectory(file)
        } else {
            showFileOptionsDialog(file)
        }
    }

    private fun showFileOptionsDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(file.name)

        val options = arrayOf(
            "View/Edit",
            "Rename",
            "Delete",
            "Cancel"
        )

        builder.setItems(options) { _, which ->
            when (which) {
                0 -> openFileEditor(file)
                1 -> renameFile(file)
                2 -> deleteFile(file)
                3 -> {}
            }
        }

        builder.show()
    }

    private fun openFileEditor(file: File) {
        try {
            val content = file.readText()
            showEditDialog(file, content)
        } catch (e: Exception) {
            Log.e("FileManagerActivity", "Error reading file: ${file.absolutePath}", e)
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEditDialog(file: File, currentContent: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit: ${file.name}")

        val editText = EditText(this)
        editText.setText(currentContent)
        editText.typeface = android.graphics.Typeface.MONOSPACE
        editText.setMinLines(10)
        editText.setMaxLines(20)
        editText.setHorizontallyScrolling(true)

        builder.setView(editText)

        builder.setPositiveButton("Save") { _, _ ->
            val newContent = editText.text.toString()
            try {
                file.writeText(newContent)
                Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
                loadDirectory(currentDir)
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Error saving file: ${file.absolutePath}", e)
                Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun renameFile(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Rename: ${file.name}")

        val editText = EditText(this)
        editText.setText(file.name)
        editText.setSelectAllOnFocus(true)

        builder.setView(editText)

        builder.setPositiveButton("Rename") { _, _ ->
            val newName = editText.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val newFile = File(file.parentFile, newName)
            if (newFile.exists()) {
                Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            try {
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDir)
                } else {
                    Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Error renaming file", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun deleteFile(file: File) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete: ${file.name}")
        builder.setMessage("Are you sure you want to delete this ${if (file.isDirectory) "folder" else "file"}?")
        builder.setPositiveButton("Delete") { _, _ ->
            try {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
                Toast.makeText(this, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                loadDirectory(currentDir)
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Error deleting file", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    private fun deleteDirectory(dir: File) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
        }
        dir.delete()
    }

    // ── Create New Folder ──
    fun onCreateFolderClick(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New Folder")
        builder.setMessage("Enter folder name:")

        val editText = EditText(this)
        builder.setView(editText)

        builder.setPositiveButton("Create") { _, _ ->
            val folderName = editText.text.toString().trim()
            if (folderName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val newFolder = File(currentDir, folderName)
            if (newFolder.exists()) {
                Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            try {
                if (newFolder.mkdir()) {
                    Toast.makeText(this, "Folder created: $folderName", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDir)
                } else {
                    Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Error creating folder", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    // ── Create New File ──
    fun onCreateFileClick(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Create New File")
        builder.setMessage("Enter file name:")

        val editText = EditText(this)
        builder.setView(editText)

        builder.setPositiveButton("Create") { _, _ ->
            val fileName = editText.text.toString().trim()
            if (fileName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            val newFile = File(currentDir, fileName)
            if (newFile.exists()) {
                Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            try {
                if (newFile.createNewFile()) {
                    Toast.makeText(this, "File created: $fileName", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDir)
                } else {
                    Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("FileManagerActivity", "Error creating file", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        builder.setNegativeButton("Cancel") { _, _ -> }
        builder.show()
    }

    // ── Go Up One Directory ──
    fun onGoUpClick(view: View) {
        val parent = currentDir.parentFile
        if (parent != null && parent.exists()) {
            loadDirectory(parent)
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    // RecyclerView adapter for files
    inner class FileAdapter(
        private val context: FileManagerActivity,
        private val onFileClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

        private var files: List<File> = emptyList()

        fun submitFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.file_item, parent, false)
            return FileViewHolder(view)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            holder.bind(files[position])
        }

        inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val nameTextView: TextView = view.findViewById(R.id.fileName)
            private val sizeTextView: TextView = view.findViewById(R.id.fileSize)
            private val iconView: ImageView = view.findViewById(R.id.fileIcon)

            fun bind(file: File) {
                nameTextView.text = file.name

                // Show file size for files, directory indicator for folders
                if (file.isDirectory) {
                    sizeTextView.text = "folder"
                    sizeTextView.setTextColor(android.graphics.Color.parseColor("#00BFFF"))
                    iconView.setImageResource(R.drawable.ic_folder)
                } else {
                    val size = file.length()
                    sizeTextView.text = formatFileSize(size)
                    sizeTextView.setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
                    iconView.setImageResource(R.drawable.ic_file)
                }

                itemView.setOnClickListener { onFileClick(file) }
            }

            private fun formatFileSize(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
                if (bytes < 1024L * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024))
                return "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
            }
        }
    }
}