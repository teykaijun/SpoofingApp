package com.spoofingmobileapp.update

import org.json.JSONException
import org.json.JSONObject

/** A published release that is newer than the installed build. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val releaseUrl: String,
)

/** A dotted version such as `1.2.10`, with or without a leading `v`. */
@JvmInline
value class Version(private val parts: List<Int>) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(index) { 0 }.compareTo(other.parts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    companion object {
        /** Returns null when [raw] is not a dotted number, e.g. a tag like `nightly`. */
        fun parse(raw: String?): Version? {
            val digits = raw?.trim()?.removePrefix("v")?.substringBefore('-') ?: return null
            if (digits.isEmpty()) return null
            val parts = digits.split('.').map { it.toIntOrNull() ?: return null }
            return Version(parts)
        }
    }
}

/**
 * Reads GitHub's "latest release" payload. Returns null when the release is a draft or
 * pre-release, carries no APK, or is not newer than [currentVersionName].
 */
fun parseLatestRelease(json: String, currentVersionName: String): UpdateInfo? {
    val release = try {
        JSONObject(json)
    } catch (e: JSONException) {
        return null
    }
    if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null

    val tag = release.optString("tag_name")
    val remote = Version.parse(tag) ?: return null
    val current = Version.parse(currentVersionName) ?: return null
    if (remote <= current) return null

    val assets = release.optJSONArray("assets") ?: return null
    for (index in 0 until assets.length()) {
        val asset = assets.optJSONObject(index) ?: continue
        if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
        val url = asset.optString("browser_download_url")
        if (url.isEmpty()) continue
        return UpdateInfo(
            versionName = tag.trim().removePrefix("v"),
            apkUrl = url,
            sizeBytes = asset.optLong("size"),
            releaseUrl = release.optString("html_url"),
        )
    }
    return null
}
