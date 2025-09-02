package com.example.fileexplorer

import java.io.File

data class BreadcrumbItem(
    val name: String,
    val file: File,
    val isLast: Boolean = false
)