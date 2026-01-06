package com.example.fileexplorer

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.File

class TextEditorActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var editText: EditText
    private lateinit var btnSave: Button
    private lateinit var btnOpenWith: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var filePath: String
    private var originalContent: String = ""
    private var hasUnsavedChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_editor)

        // 初始化视图
        initViews()

        // 获取文件路径
        filePath = intent.getStringExtra("file_path") ?: run {
            Toast.makeText(this, getString(R.string.file_read_failed, "路径为空"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(filePath)

        // 检查文件是否存在
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_read_failed, "文件不存在"), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 检查文件大小
        if (!canEditFile(file)) {
            finish()
            return
        }

        // 设置标题
        toolbar.title = file.name

        // 加载文件内容
        loadFile(file)

        // 设置按钮监听
        setupListeners()

        // 监听文本变化
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButtonState()
            }
        })
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        editText = findViewById(R.id.editText)
        btnSave = findViewById(R.id.btnSave)
        btnOpenWith = findViewById(R.id.btnOpenWith)
        progressBar = findViewById(R.id.progressBar)

        // 设置返回按钮
        toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveFile()
        }

        btnOpenWith.setOnClickListener {
            openFileWithOtherApp()
        }
    }

    private fun canEditFile(file: File): Boolean {
        val maxSize = 5 * 1024 * 1024 // 5MB
        if (file.length() > maxSize) {
            Toast.makeText(this, getString(R.string.file_size_limit), Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun loadFile(file: File) {
        progressBar.visibility = View.VISIBLE
        editText.isEnabled = false

        Thread {
            try {
                val content = file.readText(Charsets.UTF_8)

                runOnUiThread {
                    editText.setText(content)
                    originalContent = content
                    hasUnsavedChanges = false
                    progressBar.visibility = View.GONE
                    editText.isEnabled = true
                    updateSaveButtonState()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.file_read_failed, e.message ?: "未知错误"),
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                    finish()
                }
            }
        }.start()
    }

    private fun saveFile() {
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false

        Thread {
            try {
                val content = editText.text.toString()
                val file = File(filePath)

                // 原子保存操作
                val tempFile = File(filePath + ".tmp")
                tempFile.writeText(content, Charsets.UTF_8)

                // 重命名到原文件
                if (tempFile.renameTo(file)) {
                    // 重命名成功
                } else {
                    // 重命名失败，直接写入
                    file.writeText(content, Charsets.UTF_8)
                }

                // 清理临时文件
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                originalContent = content
                hasUnsavedChanges = false

                runOnUiThread {
                    Toast.makeText(this, getString(R.string.file_saved_successfully), Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    updateSaveButtonState()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.file_save_failed, e.message ?: "未知错误"),
                        Toast.LENGTH_LONG
                    ).show()
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                }
            }
        }.start()
    }

    private fun updateSaveButtonState() {
        hasUnsavedChanges = editText.text.toString() != originalContent
        btnSave.isEnabled = hasUnsavedChanges
    }

    private fun openFileWithOtherApp() {
        try {
            val file = File(filePath)
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            intent.setDataAndType(uri, getMimeType(file))
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(Intent.createChooser(intent, getString(R.string.open_with_other_app)))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "log" -> "text/plain"
            "xml" -> "text/xml"
            "json" -> "application/json"
            else -> "text/plain"
        }
    }

    private fun handleBackPress() {
        if (hasUnsavedChanges) {
            showSaveConfirmDialog()
        } else {
            finish()
        }
    }

    private fun showSaveConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_changes_title))
            .setMessage(getString(R.string.save_changes_message))
            .setPositiveButton(getString(R.string.save_changes)) { _, _ ->
                // 保存并返回
                Thread {
                    try {
                        val content = editText.text.toString()
                        File(filePath).writeText(content, Charsets.UTF_8)
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.file_saved_successfully), Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                getString(R.string.file_save_failed, e.message ?: "未知错误"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton(getString(R.string.discard_changes)) { _, _ ->
                // 不保存直接返回
                finish()
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .setCancelable(false)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackPress()
    }
}
