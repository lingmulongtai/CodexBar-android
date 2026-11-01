package com.codexbar.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class WallpaperPhoto(
    val id: String, val photographer: String, val profileUrl: String, val photoUrl: String,
    val imageUrl: String, val downloadUrl: String? = null
)

/** Keep the daily default stable, while manual refresh advances to a different available photo. */
internal fun chooseWallpaper(candidates: List<WallpaperPhoto>, date: LocalDate, currentId: String? = null,
    changePhoto: Boolean = false, avoidRepeat: Boolean = false): WallpaperPhoto? {
    val unique = candidates.distinctBy { it.id }
    if (unique.isEmpty()) return null
    val current = unique.indexOfFirst { it.id == currentId }
    if (changePhoto && current >= 0) return unique[(current + 1) % unique.size]
    val daily = Math.floorMod(date.toEpochDay(), unique.size.toLong()).toInt()
    return unique[if (avoidRepeat && unique.size > 1 && daily == current) (daily + 1) % unique.size else daily]
}

internal fun curatedWallpaper(date: LocalDate, currentId: String? = null, changePhoto: Boolean = false,
    avoidRepeat: Boolean = false): WallpaperPhoto =
    requireNotNull(chooseWallpaper(CuratedWallpapers, date, currentId, changePhoto, avoidRepeat))

/** Only public Unsplash HTTPS hosts; API authorization is never attached to image requests. */
internal fun isWallpaperUrl(value: String, host: String): Boolean = value.toHttpUrlOrNull()?.let {
    it.scheme == "https" && it.host == host && it.port == 443 && it.username.isEmpty() && it.password.isEmpty()
} == true

internal val CuratedWallpapers = listOf(
    WallpaperPhoto("2KXEb_8G5vo", "kazuend", "https://unsplash.com/@kazuend", "https://unsplash.com/photos/2KXEb_8G5vo", "https://images.unsplash.com/photo-1436891620584-47fd0e565afb"),
    WallpaperPhoto("mPnxwQBtUZE", "Casey Horner", "https://unsplash.com/@mischievous_penguins", "https://unsplash.com/photos/mPnxwQBtUZE", "https://images.unsplash.com/photo-1515825838458-f2a94b20105a"),
    WallpaperPhoto("mpGsMCn3oHE", "Federico Bottos", "https://unsplash.com/@landscapeplaces", "https://unsplash.com/photos/mpGsMCn3oHE", "https://images.unsplash.com/photo-1519068261745-b3f8ecd6b8d9"),
    WallpaperPhoto("eCohyQiC_qg", "Earth", "https://unsplash.com/@earth", "https://unsplash.com/photos/eCohyQiC_qg", "https://images.unsplash.com/photo-1522092787785-60123fde65c4"),
    WallpaperPhoto("hOhlYhAiizc", "Daniela Kokina", "https://unsplash.com/@danielakokina", "https://unsplash.com/photos/hOhlYhAiizc", "https://images.unsplash.com/photo-1615729947596-a598e5de0ab3"),
    WallpaperPhoto("Kol2ib6ehQE", "Karina Vorozheeva", "https://unsplash.com/@_k_arinn", "https://unsplash.com/photos/Kol2ib6ehQE", "https://images.unsplash.com/photo-1526336179256-1347bdb255ee")
)

internal fun selectEditorialWallpaper(data: JsonArray, date: LocalDate, currentId: String? = null,
    changePhoto: Boolean = false, avoidRepeat: Boolean = false): WallpaperPhoto? {
    val candidates = data.mapNotNull { value ->
        runCatching {
            val photo = value.jsonObject
            val width = photo["width"]!!.jsonPrimitive.int
            val height = photo["height"]!!.jsonPrimitive.int
            require(width > 0 && height > 0)
            val user = photo["user"]!!.jsonObject
            WallpaperPhoto(photo["id"]!!.jsonPrimitive.content, user["name"]!!.jsonPrimitive.content,
                user["links"]!!.jsonObject["html"]!!.jsonPrimitive.content,
                photo["links"]!!.jsonObject["html"]!!.jsonPrimitive.content,
                photo["urls"]!!.jsonObject["regular"]!!.jsonPrimitive.content,
                photo["links"]!!.jsonObject["download_location"]!!.jsonPrimitive.content).also {
                require(it.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && it.photographer.isNotBlank())
                require(isWallpaperUrl(it.imageUrl, "images.unsplash.com") && isWallpaperUrl(it.photoUrl, "unsplash.com") && isWallpaperUrl(it.profileUrl, "unsplash.com") && isWallpaperUrl(it.downloadUrl!!, "api.unsplash.com"))
            } to (height > width)
        }.getOrNull()
    }
    val portraits = candidates.filter { it.second }
    return chooseWallpaper((portraits.ifEmpty { candidates }).map { it.first }, date, currentId, changePhoto, avoidRepeat)
}
