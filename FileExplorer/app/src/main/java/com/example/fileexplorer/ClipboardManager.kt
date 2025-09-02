package com.example.fileexplorer

import java.io.File

object ClipboardManager {
    private var copiedFile: File? = null
    
    fun copyFile(file: File) {
        copiedFile = file
    }
    
    fun getCopiedFile(): File? {
        return copiedFile
    }
    
    fun hasCopiedFile(): Boolean {
        return copiedFile != null
    }
    
    fun clear() {
        copiedFile = null
    }
}