package com.codexbar.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WallpaperPolicyTest {
    private val date = LocalDate.of(2026, 10, 31)
    @Test fun `daily photos are stable during the day and rotate through the curated set`() {
        assertEquals(curatedWallpaper(date), curatedWallpaper(date))
        assertNotEquals(curatedWallpaper(date), curatedWallpaper(date.plusDays(1)))
        assertEquals(CuratedWallpapers.toSet(), (0L..5L).map { curatedWallpaper(date.plusDays(it)) }.toSet())
        assertNotNull(curatedWallpaper(LocalDate.of(1960, 1, 1)))
    }

    @Test fun `background hosts reject plaintext credentials lookalikes and nonstandard ports`() {
        assertTrue(isWallpaperUrl("https://images.unsplash.com/photo-1?ixid=retain", "images.unsplash.com"))
        listOf("http://images.unsplash.com/x", "https://images.unsplash.com.evil.example/x",
            "https://user:password@images.unsplash.com/x", "https://images.unsplash.com:8443/x", "file:///private").forEach {
            assertFalse(it, isWallpaperUrl(it, "images.unsplash.com"))
        }
        assertFalse(isWallpaperUrl("https://images.unsplash.com/x", "api.unsplash.com"))
    }

    private fun photo(id: String = "safe_photo", width: Int = 1600, image: String = "https://images.unsplash.com/photo-1?ixid=keep",
        download: String = "https://api.unsplash.com/photos/safe_photo/download?ixid=keep") = buildJsonObject {
        put("id", id); put("width", width); put("height", 1000)
        put("urls", buildJsonObject { put("regular", image) })
        put("links", buildJsonObject { put("html", "https://unsplash.com/photos/safe_photo"); put("download_location", download) })
        put("user", buildJsonObject { put("name", "Test Photographer"); put("links", buildJsonObject { put("html", "https://unsplash.com/@test") }) })
    }

    @Test fun `Editorial preserves image and download query strings for Unsplash attribution`() {
        val selected = selectEditorialWallpaper(JsonArray(listOf(photo())), date)!!
        assertEquals("https://images.unsplash.com/photo-1?ixid=keep", selected.imageUrl)
        assertEquals("https://api.unsplash.com/photos/safe_photo/download?ixid=keep", selected.downloadUrl)
    }

    @Test fun `Editorial skips portraits malformed items and unsafe URLs before choosing a background`() {
        assertNull(selectEditorialWallpaper(JsonArray(listOf(photo(width = 600), photo(id = "../../x"),
            photo(image = "https://evil.example/photo"), photo(download = "https://evil.example/steal"), JsonNull)), date))
        val selected = selectEditorialWallpaper(JsonArray(listOf(JsonNull, photo(width = 600), photo())), date)
        assertEquals("safe_photo", selected?.id)
    }
}
