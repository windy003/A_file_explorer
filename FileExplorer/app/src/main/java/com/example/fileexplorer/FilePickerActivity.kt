package com.example.fileexplorer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import java.io.File

/**
 * 文件选择器 Activity
 * 用于响应其他应用的文件选择请求 (ACTION_GET_CONTENT, ACTION_OPEN_DOCUMENT)
 */
class FilePickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FilePickerActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var breadcrumbRecyclerView: RecyclerView
    private lateinit var currentPathTextView: TextView
    private lateinit var fileAdapter: FileAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter

    private var currentDirectory: File? = null
    private var allowMultiple = false
    private var requestedMimeType: String = "*/*"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_picker)

        // 解析 Intent
        parseIntent()

        // 设置 Toolbar
        setupToolbar()

        // 初始化视图
        initViews()

        // 设置 RecyclerView
        setupRecyclerView()
        setupBreadcrumbRecyclerView()

        // 检查权限并加载文件
        if (checkStoragePermission()) {
            currentDirectory = getInitialDirectory()
            loadFiles()
        } else {
            requestStoragePermission()
        }
    }

    private fun parseIntent() {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        // 检查是否允许多选
        allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)

        // 获取请求的 MIME 类型
        requestedMimeType = intent.type ?: "*/*"
        Log.d(TAG, "Requested MIME type: $requestedMimeType, allowMultiple: $allowMultiple")
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.select_file)
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        breadcrumbRecyclerView = findViewById(R.id.breadcrumbRecyclerView)
        currentPathTextView = findViewById(R.id.tvCurrentPath)
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            mutableListOf(),
            onItemClick = { fileItem ->
                if (fileItem.isDirectory) {
                    navigateToDirectory(fileItem.file)
                } else {
                    // 检查文件类型是否匹配
                    if (isFileTypeMatched(fileItem.file)) {
                        selectFile(fileItem.file)
                    } else {
                        Toast.makeText(this, getString(R.string.file_type_not_supported), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onItemLongClick = { _ ->
                // 文件选择器模式不支持长按多选
                false
            },
            onMoreClick = { _, _ ->
                // 文件选择器模式不显示更多菜单
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@FilePickerActivity)
            adapter = fileAdapter
        }
    }

    private fun setupBreadcrumbRecyclerView() {
        breadcrumbAdapter = BreadcrumbAdapter(mutableListOf()) { breadcrumb ->
            navigateToDirectory(breadcrumb.file)
        }

        breadcrumbRecyclerView.apply {
            layoutManager = FlexboxLayoutManager(this@FilePickerActivity).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            adapter = breadcrumbAdapter
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPermission = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            readPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            }
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkStoragePermission()) {
                currentDirectory = getInitialDirectory()
                loadFiles()
            } else {
                Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                currentDirectory = getInitialDirectory()
                loadFiles()
            } else {
                Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun getInitialDirectory(): File {
        return try {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir?.exists() == true && externalDir.canRead()) {
                externalDir
            } else {
                getExternalFilesDir(null) ?: filesDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access external storage, using internal", e)
            filesDir
        }
    }

    private fun loadFiles() {
        try {
            val directory = currentDirectory ?: return
            Log.d(TAG, "Loading files from: ${directory.absolutePath}")

            val files = directory.listFiles()
            if (files == null) {
                Log.w(TAG, "Could not list files in directory: ${directory.absolutePath}")
                Toast.makeText(this, getString(R.string.cannot_access_directory), Toast.LENGTH_SHORT).show()
                return
            }

            // 过滤文件：显示所有文件夹和匹配 MIME 类型的文件
            val fileItems = files
                .filter { it.isDirectory || isFileTypeMatched(it) }
                .map { FileItem(it) }
                .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

            fileAdapter.updateFiles(fileItems)
            currentPathTextView.text = directory.absolutePath

            updateBreadcrumbs(directory)

            Log.d(TAG, "Loaded ${fileItems.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading files", e)
            Toast.makeText(this, "无法加载文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBreadcrumbs(directory: File) {
        val breadcrumbs = mutableListOf<BreadcrumbItem>()
        var current: File? = directory
        val pathList = mutableListOf<File>()

        while (current != null) {
            pathList.add(current)
            current = current.parentFile
        }

        pathList.reverse()

        for (i in pathList.indices) {
            val file = pathList[i]
            val name = if (i == 0) {
                getString(R.string.root_directory)
            } else {
                file.name
            }
            breadcrumbs.add(BreadcrumbItem(name, file, i == pathList.size - 1))
        }

        breadcrumbAdapter.updateBreadcrumbs(breadcrumbs)
    }

    private fun navigateToDirectory(directory: File) {
        if (directory.canRead()) {
            currentDirectory = directory
            loadFiles()
        } else {
            Toast.makeText(this, getString(R.string.no_permission_access_directory), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isFileTypeMatched(file: File): Boolean {
        if (requestedMimeType == "*/*") {
            return true
        }

        val fileMimeType = getMimeType(file)

        // 检查是否匹配
        if (requestedMimeType.endsWith("/*")) {
            // 如 image/* 匹配所有图片
            val requestedCategory = requestedMimeType.substringBefore("/")
            val fileCategory = fileMimeType.substringBefore("/")
            return requestedCategory == fileCategory
        }

        return fileMimeType == requestedMimeType
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "txt", "log", "md", "json", "xml", "html", "css", "js", "kt", "java", "py", "c", "cpp", "h" -> "text/plain"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "*/*"
        }
    }

    private fun selectFile(file: File) {
        try {
            // 使用 FileProvider 创建安全的 URI
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )

            val resultIntent = Intent().apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            Log.d(TAG, "File selected: ${file.absolutePath}, URI: $uri")
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Error selecting file", e)
            Toast.makeText(this, "无法选择文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        val parent = currentDirectory?.parentFile
        if (parent != null && parent.canRead()) {
            navigateToDirectory(parent)
        } else {
            setResult(Activity.RESULT_CANCELED)
            super.onBackPressed()
        }
    }
}
