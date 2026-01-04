package com.example.fileexplorer

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

class FileExplorerFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var currentPathTextView: TextView
    private lateinit var breadcrumbRecyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // 多选功能UI组件
    private lateinit var multiSelectActionBar: LinearLayout
    private lateinit var btnCopy: Button
    private lateinit var btnCut: Button
    private lateinit var btnDelete: Button
    private lateinit var btnSelectAll: Button

    // 粘贴悬浮按钮
    private lateinit var fabPaste: FloatingActionButton
    // 新建悬浮按钮
    private lateinit var fabAdd: FloatingActionButton

    private lateinit var fileAdapter: FileAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private var currentDirectory: File? = null
    private var tabId: Int = 0

    // 多选模式标志
    private var isMultiSelectMode = false

    companion object {
        private const val TAG = "FileExplorerFragment"
        private const val ARG_TAB_ID = "tab_id"
        private const val KEY_CURRENT_PATH = "current_path"
        private const val PREFS_NAME = "file_explorer_prefs"
        private const val KEY_LAST_PATH_PREFIX = "last_path_tab_"

        fun newInstance(tabId: Int) = FileExplorerFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_TAB_ID, tabId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabId = arguments?.getInt(ARG_TAB_ID) ?: 0
        Log.d(TAG, "onCreate for tab $tabId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_file_explorer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated for tab $tabId")

        try {
            initViews(view)
            setupRecyclerView()
            setupBreadcrumbRecyclerView()
            setupMultiSelectActionBar()
            setupDraggableFabs()
            setupSwipeRefresh()

            // 恢复状态或设置初始目录
            if (savedInstanceState != null) {
                restoreState(savedInstanceState)
            } else {
                currentDirectory = getInitialDirectory()
                loadFiles()
            }

            // 处理返回键
            setupBackPressHandler()

            // 初始化时更新粘贴按钮状态
            updatePasteButtonVisibility()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated", e)
            Toast.makeText(requireContext(), "Fragment初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerView)
        currentPathTextView = view.findViewById(R.id.tvCurrentPath)
        breadcrumbRecyclerView = view.findViewById(R.id.breadcrumbRecyclerView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)

        // 多选操作栏
        multiSelectActionBar = view.findViewById(R.id.multiSelectActionBar)
        btnCopy = view.findViewById(R.id.btnCopy)
        btnCut = view.findViewById(R.id.btnCut)
        btnDelete = view.findViewById(R.id.btnDelete)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)

        // 粘贴悬浮按钮
        fabPaste = view.findViewById(R.id.fabPaste)
        // 新建悬浮按钮
        fabAdd = view.findViewById(R.id.fabAdd)

        Log.d(TAG, "Views initialized for tab $tabId")
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
            onItemLongClick = { fileItem ->
                // 长按进入多选模式
                enterMultiSelectMode(fileItem)
                true
            },
            onMoreClick = { fileItem, view ->
                showFileOptionsMenu(fileItem, view)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }
    }

    private fun setupBreadcrumbRecyclerView() {
        breadcrumbAdapter = BreadcrumbAdapter(mutableListOf()) { breadcrumb ->
            navigateToDirectory(breadcrumb.file)
        }

        breadcrumbRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
    }

    private fun setupMultiSelectActionBar() {
        btnCopy.setOnClickListener { copySelectedFiles() }
        btnCut.setOnClickListener { cutSelectedFiles() }
        btnDelete.setOnClickListener { deleteSelectedFiles() }
        btnSelectAll.setOnClickListener { toggleSelectAll() }

        // 粘贴悬浮按钮点击事件
        fabPaste.setOnClickListener { pasteFile() }

        // 新建按钮点击事件
        fabAdd.setOnClickListener { showCreateMenu(it) }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isMultiSelectMode) {
                        // 多选模式下，返回键退出多选
                        exitMultiSelectMode()
                    } else {
                        // 普通模式，向上导航
                        if (!navigateUp()) {
                            isEnabled = false
                            requireActivity().onBackPressed()
                        }
                    }
                }
            }
        )
    }

    private fun setupDraggableFabs() {
        makeFabDraggable(fabPaste)
        makeFabDraggable(fabAdd)
    }

    private fun makeFabDraggable(fab: FloatingActionButton) {
        var dX = 0f
        var dY = 0f
        var lastAction = 0
        val clickThreshold = 10f // 点击阈值，移动距离小于这个值视为点击

        fab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    lastAction = MotionEvent.ACTION_DOWN
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY

                    // 获取父容器的尺寸
                    val parent = view.parent as View
                    val parentWidth = parent.width.toFloat()
                    val parentHeight = parent.height.toFloat()

                    // 边界检查
                    val clampedX = newX.coerceIn(0f, parentWidth - view.width)
                    val clampedY = newY.coerceIn(0f, parentHeight - view.height)

                    view.x = clampedX
                    view.y = clampedY

                    lastAction = MotionEvent.ACTION_MOVE
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 如果移动距离很小，视为点击事件
                    val deltaX = abs(view.x - (event.rawX + dX))
                    val deltaY = abs(view.y - (event.rawY + dY))

                    if (lastAction == MotionEvent.ACTION_DOWN ||
                        (deltaX < clickThreshold && deltaY < clickThreshold)) {
                        // 触发点击事件
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadFiles()
        }
        // 设置刷新指示器颜色
        swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.primary_dark
        )
    }

    // 更新粘贴悬浮按钮的显示状态
    private fun updatePasteButtonVisibility() {
        if (ClipboardManager.hasClipboardContent() && !isMultiSelectMode) {
            fabPaste.visibility = View.VISIBLE
        } else {
            fabPaste.visibility = View.GONE
        }
    }

    // ==================== 多选功能方法 ====================

    private fun enterMultiSelectMode(initialItem: FileItem) {
        isMultiSelectMode = true
        initialItem.isSelected = true

        fileAdapter.enterMultiSelectMode()
        multiSelectActionBar.visibility = View.VISIBLE

        updateMultiSelectUI()
        updateToolbarTitle()
        updatePasteButtonVisibility()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false

        fileAdapter.exitMultiSelectMode()
        multiSelectActionBar.visibility = View.GONE

        updateToolbarTitle()
        updatePasteButtonVisibility()
    }

    private fun updateMultiSelectUI() {
        val selectedCount = fileAdapter.getSelectedCount()

        // 更新全选按钮文本
        if (selectedCount == fileAdapter.itemCount && selectedCount > 0) {
            btnSelectAll.text = getString(R.string.deselect_all)
        } else {
            btnSelectAll.text = getString(R.string.select_all)
        }

        // 更新操作按钮启用状态
        val hasSelection = selectedCount > 0
        btnCopy.isEnabled = hasSelection
        btnCut.isEnabled = hasSelection
        btnDelete.isEnabled = hasSelection

        updateToolbarTitle()
    }

    private fun updateToolbarTitle() {
        val activity = activity as? MainActivity
        if (isMultiSelectMode) {
            val count = fileAdapter.getSelectedCount()
            activity?.supportActionBar?.title = getString(R.string.multi_select_mode, count)
        } else {
            activity?.supportActionBar?.title = getString(R.string.app_name)
        }
    }

    private fun toggleSelectAll() {
        val selectedCount = fileAdapter.getSelectedCount()

        if (selectedCount == fileAdapter.itemCount && selectedCount > 0) {
            fileAdapter.deselectAll()
        } else {
            fileAdapter.selectAll()
        }

        updateMultiSelectUI()
    }

    private fun copySelectedFiles() {
        val selectedItems = fileAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请选择文件", Toast.LENGTH_SHORT).show()
            return
        }

        val files = selectedItems.map { it.file }
        ClipboardManager.copyFiles(files)

        Toast.makeText(
            requireContext(),
            getString(R.string.copied_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()

        exitMultiSelectMode()
    }

    private fun cutSelectedFiles() {
        val selectedItems = fileAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请选择文件", Toast.LENGTH_SHORT).show()
            return
        }

        val files = selectedItems.map { it.file }
        ClipboardManager.cutFiles(files)

        Toast.makeText(
            requireContext(),
            getString(R.string.cut_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()

        exitMultiSelectMode()
    }

    private fun deleteSelectedFiles() {
        val selectedItems = fileAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请选择文件", Toast.LENGTH_SHORT).show()
            return
        }

        val count = selectedItems.size

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_delete))
            .setMessage(getString(R.string.delete_multiple_confirm, count))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                performBatchDelete(selectedItems)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performBatchDelete(items: List<FileItem>) {
        var successCount = 0
        var failCount = 0

        items.forEach { fileItem ->
            try {
                if (deleteFileRecursive(fileItem.file)) {
                    successCount++
                } else {
                    failCount++
                }
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "Delete failed: ${fileItem.name}", e)
            }
        }

        exitMultiSelectMode()
        loadFiles()

        Toast.makeText(
            requireContext(),
            getString(R.string.deleted_success, successCount),
            Toast.LENGTH_SHORT
        ).show()

        if (failCount > 0) {
            Toast.makeText(
                requireContext(),
                "删除失败: $failCount 项",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteFileRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteFileRecursive(child)) {
                    return false
                }
            }
        }
        return file.delete()
    }

    // ==================== 收藏功能 ====================

    private fun toggleFavorite(fileItem: FileItem) {
        if (!fileItem.isDirectory) {
            Toast.makeText(requireContext(), getString(R.string.only_folders_can_be_favorited), Toast.LENGTH_SHORT).show()
            return
        }

        val path = fileItem.file.absolutePath
        val isNowFavorite = FavoritesManager.toggleFavorite(path)

        val message = if (isNowFavorite) {
            getString(R.string.added_to_favorites)
        } else {
            getString(R.string.removed_from_favorites)
        }

        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // ==================== 新建功能 ====================

    private fun showCreateMenu(view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.create_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_create_folder -> {
                    showCreateFolderDialog()
                    true
                }
                R.id.action_create_file -> {
                    showCreateFileDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showCreateFolderDialog() {
        val editText = EditText(requireContext())
        editText.hint = getString(R.string.folder_name)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.create_folder))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val folderName = editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    createFolder(folderName)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.name_cannot_be_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showCreateFileDialog() {
        val editText = EditText(requireContext())
        editText.hint = getString(R.string.file_name)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.create_file))
            .setView(editText)
            .setPositiveButton(getString(R.string.create)) { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    createFile(fileName)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.name_cannot_be_empty), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createFolder(folderName: String) {
        try {
            val currentDir = currentDirectory ?: run {
                Toast.makeText(requireContext(), getString(R.string.current_dir_not_available), Toast.LENGTH_SHORT).show()
                return
            }

            val newFolder = File(currentDir, folderName)

            if (newFolder.exists()) {
                Toast.makeText(requireContext(), getString(R.string.folder_already_exists), Toast.LENGTH_SHORT).show()
                return
            }

            if (newFolder.mkdir()) {
                Toast.makeText(requireContext(), getString(R.string.folder_created_successfully), Toast.LENGTH_SHORT).show()
                loadFiles()
            } else {
                Toast.makeText(requireContext(), getString(R.string.folder_creation_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating folder", e)
            Toast.makeText(requireContext(), getString(R.string.folder_creation_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFile(fileName: String) {
        try {
            val currentDir = currentDirectory ?: run {
                Toast.makeText(requireContext(), getString(R.string.current_dir_not_available), Toast.LENGTH_SHORT).show()
                return
            }

            val newFile = File(currentDir, fileName)

            if (newFile.exists()) {
                Toast.makeText(requireContext(), getString(R.string.file_already_exists), Toast.LENGTH_SHORT).show()
                return
            }

            if (newFile.createNewFile()) {
                Toast.makeText(requireContext(), getString(R.string.file_created_successfully), Toast.LENGTH_SHORT).show()
                loadFiles()
            } else {
                Toast.makeText(requireContext(), getString(R.string.file_creation_failed), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file", e)
            Toast.makeText(requireContext(), getString(R.string.file_creation_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 原有方法（部分修改） ====================

    private fun getInitialDirectory(): File {
        // 首先尝试从 SharedPreferences 读取上次保存的路径
        val savedPath = getSavedPath()
        if (savedPath != null) {
            val savedDir = File(savedPath)
            if (savedDir.exists() && savedDir.isDirectory && savedDir.canRead()) {
                Log.d(TAG, "Restored last directory for tab $tabId: $savedPath")
                return savedDir
            }
        }

        // 如果没有保存的路径或路径无效，使用默认路径
        return try {
            val externalDir = Environment.getExternalStorageDirectory()
            if (externalDir?.exists() == true && externalDir.canRead()) {
                externalDir
            } else {
                requireContext().getExternalFilesDir(null) ?: requireContext().filesDir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access external storage, using internal", e)
            requireContext().filesDir
        }
    }

    // 从 SharedPreferences 获取保存的路径
    private fun getSavedPath(): String? {
        return try {
            val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_LAST_PATH_PREFIX + tabId, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading saved path", e)
            null
        }
    }

    // 保存当前路径到 SharedPreferences
    private fun saveCurrentPath() {
        currentDirectory?.let { dir ->
            try {
                val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_LAST_PATH_PREFIX + tabId, dir.absolutePath).apply()
                Log.d(TAG, "Saved path for tab $tabId: ${dir.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving path", e)
            }
        }
    }

    private fun loadFiles() {
        try {
            val directory = currentDirectory ?: run {
                swipeRefreshLayout.isRefreshing = false
                return
            }
            Log.d(TAG, "Loading files from: ${directory.absolutePath} for tab $tabId")

            val files = directory.listFiles()
            if (files == null) {
                Log.w(TAG, "Could not list files in directory: ${directory.absolutePath}")
                Toast.makeText(requireContext(), "无法访问此目录", Toast.LENGTH_SHORT).show()
                swipeRefreshLayout.isRefreshing = false
                return
            }

            val fileItems = files.map { FileItem(it) }
                .sortedWith(compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })

            fileAdapter.updateFiles(fileItems)
            currentPathTextView.text = directory.absolutePath

            updateBreadcrumbs(directory)

            // 更新标签页标题为当前文件夹名
            updateTabTitle(directory)

            // 保存当前路径，下次打开时恢复
            saveCurrentPath()

            Log.d(TAG, "Loaded ${fileItems.size} files for tab $tabId")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading files", e)
            Toast.makeText(requireContext(), "无法加载文件: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            // 停止刷新动画
            swipeRefreshLayout.isRefreshing = false
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

        if (breadcrumbs.isNotEmpty()) {
            breadcrumbRecyclerView.scrollToPosition(breadcrumbs.size - 1)
        }
    }

    private fun updateTabTitle(directory: File) {
        val activity = activity as? MainActivity
        val folderName = if (directory.absolutePath == Environment.getExternalStorageDirectory()?.absolutePath) {
            getString(R.string.root_directory)
        } else {
            directory.name
        }
        activity?.updateTabTitle(tabId, folderName)
    }

    private fun navigateToDirectory(directory: File) {
        if (directory.canRead()) {
            // 导航时退出多选模式
            if (isMultiSelectMode) {
                exitMultiSelectMode()
            }
            currentDirectory = directory
            loadFiles()
        } else {
            Toast.makeText(requireContext(), "无权限访问此目录", Toast.LENGTH_SHORT).show()
        }
    }

    fun navigateUp(): Boolean {
        val parent = currentDirectory?.parent
        return if (parent != null) {
            currentDirectory = File(parent)
            loadFiles()
            true
        } else {
            false
        }
    }

    // 统一处理返回键：多选模式下退出多选，否则向上导航
    fun handleBackPress(): Boolean {
        return if (isMultiSelectMode) {
            exitMultiSelectMode()
            true
        } else {
            navigateUp()
        }
    }

    // 公开方法：用于从外部（如MainActivity）打开指定文件夹
    fun openFolder(folder: File) {
        if (folder.exists() && folder.isDirectory && folder.canRead()) {
            navigateToDirectory(folder)
        } else {
            Toast.makeText(requireContext(), getString(R.string.folder_not_accessible), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFile(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().applicationContext.packageName}.fileprovider",
                file
            )
            intent.setDataAndType(uri, getMimeType(file))
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            else -> "*/*"
        }
    }

    private fun showFileOptionsMenu(fileItem: FileItem, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.file_options, popup.menu)

        val pasteItem = popup.menu.findItem(R.id.action_paste)
        pasteItem.isEnabled = ClipboardManager.hasClipboardContent()

        // 收藏选项：只对文件夹显示
        val favoriteItem = popup.menu.findItem(R.id.action_favorite)
        if (fileItem.isDirectory) {
            val isFavorite = FavoritesManager.isFavorite(fileItem.file.absolutePath)
            favoriteItem.title = if (isFavorite) {
                getString(R.string.remove_from_favorites)
            } else {
                getString(R.string.add_to_favorites)
            }
            favoriteItem.isVisible = true
        } else {
            favoriteItem.isVisible = false
        }

        // 压缩选项：只对文件夹显示
        val compressItem = popup.menu.findItem(R.id.action_compress)
        compressItem.isVisible = fileItem.isDirectory

        // 解压选项：只对zip文件显示
        val extractItem = popup.menu.findItem(R.id.action_extract)
        extractItem.isVisible = !fileItem.isDirectory && fileItem.file.extension.lowercase() == "zip"

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
                R.id.action_favorite -> {
                    toggleFavorite(fileItem)
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(fileItem)
                    true
                }
                R.id.action_copy -> {
                    // 单文件复制
                    ClipboardManager.copyFiles(listOf(fileItem.file))
                    Toast.makeText(requireContext(), getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_paste -> {
                    pasteFile()
                    true
                }
                R.id.action_compress -> {
                    compressToZip(fileItem.file)
                    true
                }
                R.id.action_extract -> {
                    extractZip(fileItem.file)
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
        val editText = EditText(requireContext())
        editText.setText(fileItem.name)

        AlertDialog.Builder(requireContext())
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
        AlertDialog.Builder(requireContext())
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
                Toast.makeText(requireContext(), "重命名成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "重命名失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "重命名失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(file: File) {
        try {
            if (deleteFileRecursive(file)) {
                loadFiles()
                Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteFile() {
        val clipboardData = ClipboardManager.getClipboardData()
        if (clipboardData == null || clipboardData.files.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_clipboard_content), Toast.LENGTH_SHORT).show()
            return
        }

        val currentDir = currentDirectory ?: return
        val isCut = ClipboardManager.isCutOperation()

        var successCount = 0
        var failCount = 0

        clipboardData.files.forEach { copiedFile ->
            try {
                // 生成唯一的目标文件名（如果需要）
                val targetFile = getUniqueFile(currentDir, copiedFile.name)

                if (isCut) {
                    // 剪切：移动文件
                    // 如果是剪切且源文件和目标文件相同，跳过
                    if (copiedFile.absolutePath == targetFile.absolutePath) {
                        successCount++
                        return@forEach
                    }

                    if (copiedFile.renameTo(targetFile)) {
                        successCount++
                    } else {
                        // renameTo 可能失败（跨分区），使用复制+删除
                        if (copiedFile.isDirectory) {
                            copyDirectory(copiedFile, targetFile)
                        } else {
                            copyRegularFile(copiedFile, targetFile)
                        }
                        if (deleteFileRecursive(copiedFile)) {
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                } else {
                    // 复制
                    if (copiedFile.isDirectory) {
                        copyDirectory(copiedFile, targetFile)
                    } else {
                        copyRegularFile(copiedFile, targetFile)
                    }
                    successCount++
                }
            } catch (e: Exception) {
                failCount++
                Log.e(TAG, "Paste failed: ${copiedFile.name}", e)
            }
        }

        // 剪切操作完成后清空剪贴板
        if (isCut) {
            ClipboardManager.clear()
        }

        loadFiles()

        val message = if (isCut) {
            getString(R.string.move_success)
        } else {
            getString(R.string.paste_success)
        }

        Toast.makeText(requireContext(), "$message: $successCount 项", Toast.LENGTH_SHORT).show()

        if (failCount > 0) {
            Toast.makeText(
                requireContext(),
                "失败: $failCount 项",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 更新粘贴按钮状态（剪切操作后会清空剪贴板）
        updatePasteButtonVisibility()
    }

    // 生成唯一的文件名，如果文件已存在则添加(1)、(2)等后缀
    private fun getUniqueFile(directory: File, originalName: String): File {
        var targetFile = File(directory, originalName)

        // 如果文件不存在，直接返回
        if (!targetFile.exists()) {
            return targetFile
        }

        // 分离文件名和扩展名
        val nameWithoutExtension: String
        val extension: String

        val lastDotIndex = originalName.lastIndexOf('.')
        if (lastDotIndex > 0 && lastDotIndex < originalName.length - 1) {
            // 有扩展名
            nameWithoutExtension = originalName.substring(0, lastDotIndex)
            extension = originalName.substring(lastDotIndex) // 包含点号
        } else {
            // 没有扩展名或者是隐藏文件（以.开头）
            nameWithoutExtension = originalName
            extension = ""
        }

        // 尝试添加(1)、(2)、(3)等后缀，直到找到不存在的文件名
        var counter = 1
        while (targetFile.exists()) {
            val newName = "$nameWithoutExtension($counter)$extension"
            targetFile = File(directory, newName)
            counter++

            // 防止无限循环（虽然不太可能）
            if (counter > 1000) {
                break
            }
        }

        return targetFile
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentDirectory?.let {
            outState.putString(KEY_CURRENT_PATH, it.absolutePath)
        }
        Log.d(TAG, "Saved state for tab $tabId: ${currentDirectory?.absolutePath}")
    }

    private fun restoreState(savedInstanceState: Bundle) {
        val path = savedInstanceState.getString(KEY_CURRENT_PATH)
        currentDirectory = path?.let { File(it) } ?: getInitialDirectory()
        loadFiles()
        Log.d(TAG, "Restored state for tab $tabId: ${currentDirectory?.absolutePath}")
    }

    override fun onResume() {
        super.onResume()
        // 切换标签时更新粘贴按钮状态
        updatePasteButtonVisibility()
    }

    // ==================== 压缩/解压功能 ====================

    private fun compressToZip(folder: File) {
        if (!folder.isDirectory) {
            Toast.makeText(requireContext(), getString(R.string.compress_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val parentDir = folder.parentFile ?: return
        val zipFile = getUniqueFile(parentDir, "${folder.name}.zip")

        Toast.makeText(requireContext(), getString(R.string.compressing), Toast.LENGTH_SHORT).show()

        Thread {
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                    zipFolder(folder, folder.name, zos)
                }

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), getString(R.string.compress_success), Toast.LENGTH_SHORT).show()
                    loadFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Compress failed", e)
                // 压缩失败时删除可能创建的不完整文件
                zipFile.delete()
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "${getString(R.string.compress_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun zipFolder(folder: File, basePath: String, zos: ZipOutputStream) {
        val files = folder.listFiles() ?: return

        for (file in files) {
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                // 添加目录条目
                zos.putNextEntry(ZipEntry("$entryPath/"))
                zos.closeEntry()
                // 递归压缩子目录
                zipFolder(file, entryPath, zos)
            } else {
                // 添加文件
                zos.putNextEntry(ZipEntry(entryPath))
                BufferedInputStream(FileInputStream(file)).use { bis ->
                    bis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun extractZip(zipFile: File) {
        if (!zipFile.exists() || zipFile.extension.lowercase() != "zip") {
            Toast.makeText(requireContext(), getString(R.string.extract_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val parentDir = zipFile.parentFile ?: return
        // 创建与zip文件同名的文件夹（不含扩展名）
        val folderName = zipFile.nameWithoutExtension
        val targetFolder = getUniqueFile(parentDir, folderName)

        Toast.makeText(requireContext(), getString(R.string.extracting), Toast.LENGTH_SHORT).show()

        Thread {
            try {
                targetFolder.mkdirs()

                ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(targetFolder, entry.name)

                        // 安全检查：防止zip slip攻击
                        if (!entryFile.canonicalPath.startsWith(targetFolder.canonicalPath)) {
                            throw SecurityException("ZIP entry is outside of the target directory")
                        }

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            // 确保父目录存在
                            entryFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(entryFile)).use { bos ->
                                zis.copyTo(bos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), getString(R.string.extract_success), Toast.LENGTH_SHORT).show()
                    loadFiles()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Extract failed", e)
                // 解压失败时删除可能创建的不完整目录
                deleteFileRecursive(targetFolder)
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "${getString(R.string.extract_failed)}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
