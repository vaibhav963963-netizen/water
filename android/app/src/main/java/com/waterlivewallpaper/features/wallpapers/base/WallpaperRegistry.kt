package com.waterlivewallpaper.features.wallpapers.base

import com.waterlivewallpaper.R

object WallpaperRegistry {

    const val WALLPAPER_ID_WATER = "live_waterpaper"
    const val WALLPAPER_ID_STICKS = "floating_sticks_water"
    const val WALLPAPER_ID_NEBULA = "cosmic_nebula"
    const val WALLPAPER_ID_MAGMA = "molten_magma"
    const val WALLPAPER_ID_MATRIX = "matrix_rain"

    val wallpapers: List<WallpaperItem> = listOf(
        WallpaperItem(
            id = WALLPAPER_ID_WATER,
            title = "Live WaterPaper",
            tagline = "Authentic SPH fluid dynamics with tilt & incompressibility physics",
            description = "Interactive liquid water with real-time sensor reaction, non-linear metaball glow, and fluid volume preservation.",
            category = WallpaperCategory.LIQUID,
            iconResId = R.drawable.sticker_water_drop_3d,
            thumbnailResId = R.drawable.wallpaper_thumb,
            isInstalled = true,
            isAvailable = true,
            version = "1.0.2"
        ),
        WallpaperItem(
            id = WALLPAPER_ID_STICKS,
            title = "Floating Sticks Wallpaper",
            tagline = "3D wooden driftwood twigs floating with tilt, shake, and collisions",
            description = "Interactive 3D wooden driftwood twigs on canvas reacting to gravity tilt, touch ripples, rotational torque, and real-time rigid body collisions.",
            category = WallpaperCategory.NATURE,
            iconResId = R.drawable.stick_twig_1,
            thumbnailResId = R.drawable.stick_twig_1,
            isInstalled = true,
            isAvailable = true,
            version = "1.0.0"
        ),
        WallpaperItem(
            id = WALLPAPER_ID_NEBULA,
            title = "Cosmic Nebula",
            tagline = "Deep space stellar dust clouds with 3D starfield parallax",
            description = "Volumetric gas dynamics with procedural stars and multi-layer cosmic radiance.",
            category = WallpaperCategory.SPACE,
            iconResId = R.drawable.sticker_water_drop_3d,
            thumbnailResId = R.drawable.wallpaper_thumb,
            isInstalled = false,
            isAvailable = false,
            version = "Upcoming"
        ),
        WallpaperItem(
            id = WALLPAPER_ID_MAGMA,
            title = "Molten Magma",
            tagline = "Viscous volcanic lava with incandescent thermal glow",
            description = "High-viscosity fluid simulation featuring floating obsidian crusts and magma bubbles.",
            category = WallpaperCategory.LIQUID,
            iconResId = R.drawable.sticker_water_drop_3d,
            thumbnailResId = R.drawable.wallpaper_thumb,
            isInstalled = false,
            isAvailable = false,
            version = "Upcoming"
        ),
        WallpaperItem(
            id = WALLPAPER_ID_MATRIX,
            title = "Matrix Digital Rain",
            tagline = "Cascading phosphor glyphs with gyro-reactive perspective shifts",
            description = "Cyberpunk digital glyph stream reacting to orientation sensors with neon phosphor blooming.",
            category = WallpaperCategory.ABSTRACT,
            iconResId = R.drawable.sticker_toolbox_3d,
            thumbnailResId = R.drawable.wallpaper_thumb,
            isInstalled = false,
            isAvailable = false,
            version = "Upcoming"
        )
    )

    fun getWallpaperById(id: String): WallpaperItem? {
        return wallpapers.firstOrNull { it.id == id }
    }
}
