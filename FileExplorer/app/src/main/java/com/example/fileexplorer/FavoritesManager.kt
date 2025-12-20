package com.example.fileexplorer

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object FavoritesManager {
    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITES = "favorite_paths"
    private const val SEPARATOR = "|||"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 添加收藏
    fun addFavorite(path: String): Boolean {
        val favorites = getFavorites().toMutableSet()
        val added = favorites.add(path)
        if (added) {
            saveFavorites(favorites)
        }
        return added
    }

    // 移除收藏
    fun removeFavorite(path: String): Boolean {
        val favorites = getFavorites().toMutableSet()
        val removed = favorites.remove(path)
        if (removed) {
            saveFavorites(favorites)
        }
        return removed
    }

    // 检查是否已收藏
    fun isFavorite(path: String): Boolean {
        return getFavorites().contains(path)
    }

    // 获取所有收藏
    fun getFavorites(): Set<String> {
        val favoritesString = prefs.getString(KEY_FAVORITES, "") ?: ""
        return if (favoritesString.isEmpty()) {
            emptySet()
        } else {
            favoritesString.split(SEPARATOR).toSet()
        }
    }

    // 获取有效的收藏（过滤掉不存在的路径）
    fun getValidFavorites(): List<File> {
        val favorites = getFavorites()
        val validFavorites = favorites
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }

        // 如果发现有无效的收藏，清理掉
        val validPaths = validFavorites.map { it.absolutePath }.toSet()
        if (validPaths.size != favorites.size) {
            saveFavorites(validPaths)
        }

        return validFavorites.sortedBy { it.name.lowercase() }
    }

    // 切换收藏状态
    fun toggleFavorite(path: String): Boolean {
        return if (isFavorite(path)) {
            removeFavorite(path)
            false
        } else {
            addFavorite(path)
            true
        }
    }

    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit()
            .putString(KEY_FAVORITES, favorites.joinToString(SEPARATOR))
            .apply()
    }

    // 清空所有收藏
    fun clearAll() {
        prefs.edit().remove(KEY_FAVORITES).apply()
    }
}
