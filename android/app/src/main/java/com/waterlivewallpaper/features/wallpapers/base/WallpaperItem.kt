package com.waterlivewallpaper.features.wallpapers.base

import androidx.annotation.DrawableRes

enum class WallpaperCategory {
    LIQUID,
    SPACE,
    ABSTRACT,
    NATURE
}

data class WallpaperItem(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val category: WallpaperCategory,
    @DrawableRes val iconResId: Int,
    @DrawableRes val thumbnailResId: Int,
    val isInstalled: Boolean = true,
    val isAvailable: Boolean = true,
    val version: String = "1.0.2"
)
