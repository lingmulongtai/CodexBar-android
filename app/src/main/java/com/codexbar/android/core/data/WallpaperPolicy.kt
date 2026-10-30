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

internal fun curatedWallpaper(date: LocalDate): WallpaperPhoto = CuratedWallpapers[Math.floorMod(date.toEpochDay(), CuratedWallpapers.size.toLong()).toInt()]

/** Only public Unsplash HTTPS hosts; API authorization is never attached to image requests. */
internal fun isWallpaperUrl(value: String, host: String): Boolean = value.toHttpUrlOrNull()?.let {
    it.scheme == "https" && it.host == host && it.port == 443 && it.username.isEmpty() && it.password.isEmpty()
} == true

internal val CuratedWallpapers = listOf(
    WallpaperPhoto("VowIFDxogG4", "Luca Bravo", "https://unsplash.com/@lucabravo", "https://unsplash.com/photos/VowIFDxogG4", "https://images.unsplash.com/photo-1470770903676-69b98201ea1c"),
    WallpaperPhoto("sp-p7uuT0tw", "Sebastian Unrau", "https://unsplash.com/@sebastian_unrau", "https://unsplash.com/photos/sp-p7uuT0tw", "https://images.unsplash.com/photo-1448375240586-882707db888b"),
    WallpaperPhoto("_v-EHHKKW3w", "Adrian Infernus", "https://unsplash.com/@adrian_infernus", "https://unsplash.com/photos/_v-EHHKKW3w", "https://images.unsplash.com/photo-1543871645-b3be1624a0fc"),
    WallpaperPhoto("qfcBVgqJ_1E", "Ricardo Gomez Angel", "https://unsplash.com/@rgaleriacom", "https://unsplash.com/photos/qfcBVgqJ_1E", "https://images.unsplash.com/photo-1527749561912-77dfac6274db"),
    WallpaperPhoto("zp_iEPfwvw0", "Wolfgang Hasselmann", "https://unsplash.com/@wolfgang_hasselmann", "https://unsplash.com/photos/zp_iEPfwvw0", "https://images.unsplash.com/photo-1716492444249-e5a6981daf58"),
    WallpaperPhoto("dJVU4jXV1Q8", "Sebastian Unrau", "https://unsplash.com/@sebastian_unrau", "https://unsplash.com/photos/dJVU4jXV1Q8", "https://images.unsplash.com/photo-1440847899694-90043f91c7f9")
)

internal fun selectEditorialWallpaper(data: JsonArray, date: LocalDate): WallpaperPhoto? {
    val candidates = data.mapNotNull { value ->
        runCatching {
            val photo = value.jsonObject
            require(photo["width"]!!.jsonPrimitive.int >= photo["height"]!!.jsonPrimitive.int)
            val user = photo["user"]!!.jsonObject
            WallpaperPhoto(photo["id"]!!.jsonPrimitive.content, user["name"]!!.jsonPrimitive.content,
                user["links"]!!.jsonObject["html"]!!.jsonPrimitive.content,
                photo["links"]!!.jsonObject["html"]!!.jsonPrimitive.content,
                photo["urls"]!!.jsonObject["regular"]!!.jsonPrimitive.content,
                photo["links"]!!.jsonObject["download_location"]!!.jsonPrimitive.content).also {
                require(it.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && it.photographer.isNotBlank())
                require(isWallpaperUrl(it.imageUrl, "images.unsplash.com") && isWallpaperUrl(it.photoUrl, "unsplash.com") && isWallpaperUrl(it.profileUrl, "unsplash.com") && isWallpaperUrl(it.downloadUrl!!, "api.unsplash.com"))
            }
        }.getOrNull()
    }
    if (candidates.isEmpty()) return null
    return candidates[Math.floorMod(date.toEpochDay(), candidates.size.toLong()).toInt()]
}
