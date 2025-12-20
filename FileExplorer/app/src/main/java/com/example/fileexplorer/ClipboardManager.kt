package com.example.fileexplorer

import java.io.File

// 操作类型枚举
enum class ClipboardOperationType {
    COPY,  // 复制
    CUT    // 剪切（移动）
}

// 剪贴板数据类
data class ClipboardData(
    val files: List<File>,
    val operationType: ClipboardOperationType
)

object ClipboardManager {
    private var clipboardData: ClipboardData? = null

    // 复制文件（多个）
    fun copyFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.COPY)
    }

    // 剪切文件（多个）
    fun cutFiles(files: List<File>) {
        clipboardData = ClipboardData(files, ClipboardOperationType.CUT)
    }

    // 获取剪贴板数据
    fun getClipboardData(): ClipboardData? {
        return clipboardData
    }

    // 检查是否有剪贴板内容
    fun hasClipboardContent(): Boolean {
        return clipboardData != null && clipboardData!!.files.isNotEmpty()
    }

    // 检查是否为剪切操作
    fun isCutOperation(): Boolean {
        return clipboardData?.operationType == ClipboardOperationType.CUT
    }

    // 清空剪贴板
    fun clear() {
        clipboardData = null
    }
}
