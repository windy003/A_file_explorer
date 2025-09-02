package com.example.fileexplorer

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import java.io.File

class SimpleMainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentPathTextView: TextView
    private lateinit var fileAdapter: FileAdapter
    
    companion object {
        private const val TAG = "SimpleMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        
        try {
            setContentView(R.layout.activity_simple_main)
            
            initViews()
            setupRecyclerView()
            loadInternalFiles()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "应用启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        currentPathTextView = findViewById(R.id.tvCurrentPath)
        
        Log.d(TAG, "Views initialized successfully")
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            mutableListOf(),
            onItemClick = { fileItem ->
                if (fileItem.isDirectory) {
                    Toast.makeText(this, "点击了文件夹: ${fileItem.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "点击了文件: ${fileItem.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onMoreClick = { fileItem, _ ->
                Toast.makeText(this, "更多选项: ${fileItem.name}", Toast.LENGTH_SHORT).show()
            }
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@SimpleMainActivity)
            adapter = fileAdapter
        }
    }

    private fun loadInternalFiles() {
        try {
            // 使用应用内部存储，不需要权限
            val internalDir = filesDir
            currentPathTextView.text = "内部存储: ${internalDir.absolutePath}"
            
            // 创建一些示例文件夹以展示功能
            val folders = listOf("Documents", "Pictures", "Music", "Downloads")
            val files = listOf("example.txt", "readme.md", "settings.json")
            
            val fileItems = mutableListOf<FileItem>()
            
            // 添加文件夹
            folders.forEach { folderName ->
                val folder = File(internalDir, folderName)
                if (!folder.exists()) folder.mkdirs()
                fileItems.add(FileItem(folder))
            }
            
            // 添加文件
            files.forEach { fileName ->
                val file = File(internalDir, fileName)
                if (!file.exists()) {
                    file.writeText("这是一个示例文件: $fileName")
                }
                fileItems.add(FileItem(file))
            }
            
            fileAdapter.updateFiles(fileItems.sortedWith(
                compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ))
            
            Log.d(TAG, "Loaded ${fileItems.size} items from internal storage")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading internal files", e)
            Toast.makeText(this, "加载文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}