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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import android.os.StatFs
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var permissionLayout: LinearLayout
    private lateinit var grantPermissionButton: Button
    private lateinit var storageInfoText: TextView

    private lateinit var pagerAdapter: TabPagerAdapter
    private lateinit var tabLayoutMediator: TabLayoutMediator

    companion object {
        private const val TAG = "MainActivity"
        private const val STORAGE_PERMISSION_CODE = 100
        private const val MANAGE_STORAGE_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")

        try {
            // 初始化收藏夹管理器
            FavoritesManager.init(this)

            setContentView(R.layout.activity_main)

            initViews()
            setupToolbar()
            setupViewPager()

            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "应用启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        try {
            toolbar = findViewById(R.id.toolbar)
            tabLayout = findViewById(R.id.tabLayout)
            viewPager = findViewById(R.id.viewPager)
            permissionLayout = findViewById(R.id.permissionLayout)
            grantPermissionButton = findViewById(R.id.btnGrantPermission)
            storageInfoText = findViewById(R.id.storageInfoText)

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
                // 获取当前Fragment并尝试返回上级
                val currentFragment = supportFragmentManager
                    .findFragmentByTag("f${viewPager.currentItem}") as? FileExplorerFragment
                val handled = currentFragment?.navigateUp() ?: false

                if (!handled) {
                    // Fragment无法返回上级，退出应用
                    finish()
                }
            }
            Log.d(TAG, "Toolbar setup successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set toolbar as action bar, using as regular toolbar", e)
            toolbar.title = getString(R.string.app_name)
            toolbar.setNavigationOnClickListener {
                val currentFragment = supportFragmentManager
                    .findFragmentByTag("f${viewPager.currentItem}") as? FileExplorerFragment
                val handled = currentFragment?.navigateUp() ?: false
                if (!handled) {
                    finish()
                }
            }
        }
    }

    private fun setupViewPager() {
        pagerAdapter = TabPagerAdapter(this)
        viewPager.adapter = pagerAdapter

        // 设置预加载所有标签页，确保所有Fragment都被创建并更新标签名
        // offscreenPageLimit = 标签总数 - 1
        viewPager.offscreenPageLimit = 2 // 3个标签，所以是2

        // 关联TabLayout和ViewPager2
        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // 初始默认标签名，Fragment加载后会更新为文件夹名
            tab.text = "..."
        }
        tabLayoutMediator.attach()

        Log.d(TAG, "ViewPager setup successfully")
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
        viewPager.visibility = View.GONE

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
        viewPager.visibility = View.VISIBLE
        // Fragment会在创建时自动加载文件，无需手动刷新
        // 更新存储空间信息
        updateStorageInfo()
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
                    viewPager.visibility = View.GONE
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
                        viewPager.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        // 获取当前Fragment并尝试返回上级目录
        val currentFragment = supportFragmentManager
            .findFragmentByTag("f${viewPager.currentItem}") as? FileExplorerFragment

        val handled = currentFragment?.navigateUp() ?: false

        if (!handled) {
            // Fragment无法返回上级，执行默认返回（退出应用）
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorites -> {
                showFavoritesDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFavoritesDialog() {
        val favorites = FavoritesManager.getValidFavorites()

        if (favorites.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_favorites), Toast.LENGTH_SHORT).show()
            return
        }

        val favoriteNames = favorites.map { it.absolutePath }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.favorites))
            .setItems(favoriteNames) { _, which ->
                val selectedFolder = favorites[which]
                openFolderInCurrentTab(selectedFolder)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    fun openFolderInCurrentTab(folder: File) {
        val currentFragment = supportFragmentManager
            .findFragmentByTag("f${viewPager.currentItem}") as? FileExplorerFragment
        currentFragment?.openFolder(folder)
    }

    // 更新指定标签页的标题
    fun updateTabTitle(tabId: Int, title: String) {
        val tab = tabLayout.getTabAt(tabId)
        tab?.text = title
    }

    // 更新存储空间信息
    private fun updateStorageInfo() {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)

            val totalBytes = stat.blockSizeLong * stat.blockCountLong
            val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
            val usedBytes = totalBytes - availableBytes

            val usedSpace = formatFileSize(usedBytes)
            val totalSpace = formatFileSize(totalBytes)

            storageInfoText.text = getString(R.string.storage_info, usedSpace, totalSpace)
            Log.d(TAG, "Storage info updated: $usedSpace / $totalSpace")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating storage info", e)
            storageInfoText.text = getString(R.string.loading_storage_info)
        }
    }

    // 格式化文件大小
    private fun formatFileSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024
        val tb = gb * 1024

        return when {
            size >= tb -> String.format("%.2f TB", size.toDouble() / tb)
            size >= gb -> String.format("%.2f GB", size.toDouble() / gb)
            size >= mb -> String.format("%.2f MB", size.toDouble() / mb)
            size >= kb -> String.format("%.2f KB", size.toDouble() / kb)
            else -> "$size B"
        }
    }
}
