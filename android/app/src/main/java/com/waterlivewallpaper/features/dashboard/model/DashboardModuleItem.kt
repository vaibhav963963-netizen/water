package com.waterlivewallpaper.features.dashboard.model

import androidx.annotation.DrawableRes

data class DashboardModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    @DrawableRes val stickerResId: Int,
    val badge: String,
    val isAvailable: Boolean = false
)
