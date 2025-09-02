package com.example.fileexplorer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Button
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var currentPathTextView: TextView
    private lateinit var breadcrumbRecyclerView: RecyclerView
    private lateinit var permissionLayout: LinearLayout
    private lateinit var grantPermissionButton: Button
    
    private lateinit var fileAdapter: FileAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private var currentDirectory: File? = null
    
    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_CODE = 100
        private const val MANAGE_STORAGE_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            setContentView(R.layout.activity_main)
            
            initViews()
            setupToolbar()
            setupRecyclerView()
            
            // 设置初始目录，使用安全的默认路径
            currentDirectory = getInitialDirectory()
            
            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "应用启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getInitialDirectory(): File {
        return try {
            // 优先使用外部存储，如果无法访问则使用应用内部目录
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir?.exists() == true && externalDir.canRead()) {
                externalDir
            } else {
                // 使用应用的外部文件目录作为备选
                getExternalFilesDir(null) ?: filesDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access external storage, using internal", e)
            filesDir
        }
    }

    private fun initViews() {
        try {
            recyclerView = findViewById(R.id.recyclerView)
            toolbar = findViewById(R.id.toolbar)
            currentPathTextView = findViewById(R.id.tvCurrentPath)
            breadcrumbRecyclerView = findViewById(R.id.breadcrumbRecyclerView)
            permissionLayout = findViewById(R.id.permissionLayout)
            grantPermissionButton = findViewById(R.id.btnGrantPermission)
            
            grantPermissionButton.setOnClickListener {
                checkAndRequestPermissions()
            }
            Log.d(TAG, "Views initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views", e)
            throw e
        }
    }

    private fun setupToolbar() {
        try {
            setSupportActionBar(toolbar)
            supportActionBar?.title = getString(R.string.app_name)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            
            toolbar.setNavigationOnClickListener {
                navigateUp()
            }
            Log.d(TAG, "Toolbar setup successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set toolbar as action bar, using as regular toolbar", e)
            // 如果设置失败，直接设置标题
            toolbar.title = getString(R.string.app_name)
            toolbar.setNavigationOnClickListener {
                navigateUp()
            }
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            mutableListOf(),
            onItemClick = { fileItem ->
                if (fileItem.isDirectory) {
                    navigateToDirectory(fileItem.file)
                } else {
                    openFile(fileItem.file)
                }
            },
            onMoreClick = { fileItem, view ->
                showFileOptionsMenu(fileItem, view)
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = fileAdapter
        }
        
        setupBreadcrumbRecyclerView()
    }

    private fun checkAndRequestPermissions() {
        try {
            Log.d(TAG, "Checking permissions")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ 检查所有文件访问权限
                if (Environment.isExternalStorageManager()) {
                    Log.d(TAG, "All files access permission granted")
                    permissionGranted()
                } else {
                    Log.d(TAG, "Requesting all files access permission")
                    showPermissionUI()
                }
            } else {
                // Android 10 及以下检查存储权限
                val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                
                if (readPermission == PackageManager.PERMISSION_GRANTED && writePermission == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permissions granted")
                    permissionGranted()
                } else {
                    Log.d(TAG, "Requesting storage permissions")
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        STORAGE_PERMISSION_CODE
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            // 如果权限检查失败，直接尝试使用内部存储
            permissionGranted()
        }
    }

    private fun showPermissionUI() {
        permissionLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        
        grantPermissionButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, MANAGE_STORAGE_PERMISSION_CODE)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not open permission settings", e)
                    Toast.makeText(this, "请在设置中手动授予存储权限", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun permissionGranted() {
        permissionLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        loadFiles()
    }

    private fun setupBreadcrumbRecyclerView() {
        breadcrumbAdapter = BreadcrumbAdapter(mutableListOf()) { breadcrumb ->
            navigateToDirectory(breadcrumb.file)
        }
        
        breadcrumbRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
    }
    
    private fun loadFiles() {
        try {
            val directory = currentDirectory ?: return
            Log.d(TAG, "Loading files from: ${directory.absolutePath}")
            
            val files = directory.listFiles()
            if (files == null) {
                Log.w(TAG, "Could not list files in directory: ${directory.absolutePath}")
                Toast.makeText(this, "无法访问此目录", Toast.LENGTH_SHORT).show()
                return
            }
            
            val fileItems = files.map { FileItem(it) }
                .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
            
            fileAdapter.updateFiles(fileItems)
            currentPathTextView.text = directory.absolutePath
            
            // 更新面包屑导航
            updateBreadcrumbs(directory)
            
            // 更新返回按钮状态
            val canGoUp = directory.parent != null
            supportActionBar?.setDisplayHomeAsUpEnabled(canGoUp)
            
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
        
        // 构建路径列表
        while (current != null) {
            pathList.add(current)
            current = current.parentFile
        }
        
        // 反转列表以获得正确的顺序
        pathList.reverse()
        
        // 创建面包屑项目
        for (i in pathList.indices) {
            val file = pathList[i]
            val name = if (i == 0) {
                // 根目录显示为"根目录"
                getString(R.string.root_directory)
            } else {
                file.name
            }
            breadcrumbs.add(BreadcrumbItem(name, file, i == pathList.size - 1))
        }
        
        breadcrumbAdapter.updateBreadcrumbs(breadcrumbs)
        
        // 滚动到最后一个面包屑
        if (breadcrumbs.isNotEmpty()) {
            breadcrumbRecyclerView.scrollToPosition(breadcrumbs.size - 1)
        }
    }

    private fun navigateToDirectory(directory: File) {
        if (directory.canRead()) {
            currentDirectory = directory
            loadFiles()
        } else {
            Toast.makeText(this, "无权限访问此目录", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateUp() {
        currentDirectory?.parent?.let { parentPath ->
            currentDirectory = File(parentPath)
            loadFiles()
        }
    }

    private fun openFile(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            intent.setDataAndType(uri, getMimeType(file))
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }

    private fun showFileOptionsMenu(fileItem: FileItem, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.file_options, popup.menu)
        
        // 根据剪贴板状态启用/禁用粘贴选项
        val pasteItem = popup.menu.findItem(R.id.action_paste)
        pasteItem.isEnabled = ClipboardManager.hasCopiedFile()
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_open -> {
                    if (fileItem.isDirectory) {
                        navigateToDirectory(fileItem.file)
                    } else {
                        openFile(fileItem.file)
                    }
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(fileItem)
                    true
                }
                R.id.action_copy -> {
                    copyFile(fileItem)
                    true
                }
                R.id.action_paste -> {
                    pasteFile()
                    true
                }
                R.id.action_delete -> {
                    showDeleteDialog(fileItem)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameDialog(fileItem: FileItem) {
        val editText = EditText(this)
        editText.setText(fileItem.name)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename))
            .setMessage(getString(R.string.enter_new_name))
            .setView(editText)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != fileItem.name) {
                    renameFile(fileItem.file, newName)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDeleteDialog(fileItem: FileItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(getString(R.string.delete_confirm_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteFile(fileItem.file)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun renameFile(file: File, newName: String) {
        try {
            val newFile = File(file.parent, newName)
            if (file.renameTo(newFile)) {
                loadFiles()
                Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "重命名失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(file: File) {
        try {
            if (file.delete()) {
                loadFiles()
                Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && 
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    permissionGranted()
                } else {
                    permissionLayout.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            MANAGE_STORAGE_PERMISSION_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        permissionGranted()
                    } else {
                        permissionLayout.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun copyFile(fileItem: FileItem) {
        ClipboardManager.copyFile(fileItem.file)
        Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }
    
    private fun pasteFile() {
        val copiedFile = ClipboardManager.getCopiedFile()
        if (copiedFile == null) {
            Toast.makeText(this, getString(R.string.no_clipboard_content), Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentDir = currentDirectory ?: return
        val targetFile = File(currentDir, copiedFile.name)
        
        try {
            if (copiedFile.isDirectory) {
                copyDirectory(copiedFile, targetFile)
            } else {
                copyRegularFile(copiedFile, targetFile)
            }
            
            loadFiles()
            Toast.makeText(this, getString(R.string.paste_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.paste_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun copyRegularFile(source: File, target: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
    
    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val targetChild = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetChild)
            } else {
                copyRegularFile(file, targetChild)
            }
        }
    }

    override fun onBackPressed() {
        if (currentDirectory?.parent != null) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }
}