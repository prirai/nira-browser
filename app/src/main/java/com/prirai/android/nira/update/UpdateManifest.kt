package com.prirai.android.nira.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalised view of a single GitHub release that the auto-updater
 * cares about. Everything else in the GitHub API payload is dropped
 * at parse time so the serialised form we persist in SharedPreferences
 * (via [toJson]) stays small.
 *
 * The structure mirrors the raw REST response just enough to keep
 * parsing straightforward: [UpdateAsset.name] / [UpdateAsset.sizeBytes]
 * / [UpdateAsset.downloadUrl] come straight from each entry in the
 * `assets` array, and [tagName] / [name] / [body] / [publishedAt] are
 * top-level fields.
 *
 * `versionCode` is **not** carried by GitHub's API - it is derived
 * locally from [tagName] via [parseVersionCodeFromTag]. If the tag
 * ever stops following the `vMAJOR.MINOR.PATCH` convention this
 * returns null and the updater reports an error rather than
 * silently guessing.
 */
internal data class UpdateManifest(
    /** e.g. `"v0.5.15"`. Used to recover [versionCode]. */
    val tagName: String,

    /** Human-visible version e.g. `"v0.5.15"` or `"Release v0.5.15"`. */
    val name: String,

    /** Release notes (markdown as provided by GitHub). */
    val body: String,

    /** ISO-8601 publish timestamp as reported by GitHub. */
    val publishedAt: String,

    /** The GitHub assets we recognise as APKs. */
    val assets: List<UpdateAsset>,
) {
    /**
     * Monotonic version derived from [tagName]. For a `vA.B.C` tag,
     * returns `A * 10000 + B * 100 + C` (matches the arithmetic in
     * `app/build.gradle`'s versionCode computation for recent
     * releases - v0.5.15 -> 515 -> *100 -> 51500 -> fits in 32 bits
     * with plenty of room).
     *
     * Returns `null` if the tag does not parse.
     */
    val versionCode: Int?
        get() = parseVersionCodeFromTag(tagName)

    fun toJson(): String {
        val assetsArr = JSONArray()
        assets.forEach { assetsArr.put(it.toJson()) }
        return JSONObject().apply {
            put("tagName", tagName)
            put("name", name)
            put("body", body)
            put("publishedAt", publishedAt)
            put("assets", assetsArr)
        }.toString()
    }

    companion object {
        /**
         * Parse the response body of
         * `GET /repos/prirai/nira-browser/releases/latest`. Returns
         * `null` if any required field is missing - we never throw
         * from here so a malformed response just becomes a cleanly
         * reported "no update" result.
         */
        fun fromReleaseJson(json: String): UpdateManifest? = try {
            val obj = JSONObject(json)
            val assetsRaw = obj.optJSONArray("assets") ?: JSONArray()
            val assets = buildList {
                for (i in 0 until assetsRaw.length()) {
                    val a = assetsRaw.optJSONObject(i) ?: continue
                    val name = a.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val url = a.optString("browser_download_url")
                        .takeIf { it.isNotBlank() } ?: continue
                    val size = a.optLong("size", -1L).takeIf { it > 0 } ?: continue
                    add(UpdateAsset(name = name, downloadUrl = url, sizeBytes = size))
                }
            }
            val tag = obj.optString("tag_name").takeIf { it.isNotBlank() }
                ?: return@fromReleaseJson null
            UpdateManifest(
                tagName = tag,
                name = obj.optString("name").ifBlank { tag },
                body = obj.optString("body"),
                publishedAt = obj.optString("published_at"),
                assets = assets,
            )
        } catch (t: Throwable) {
            null
        }

        /**
         * Rehydrate the manifest written by [toJson]. Returns `null`
         * on any parse failure - callers treat this as "no cached
         * manifest, re-fetch".
         */
        fun fromCachedJson(json: String): UpdateManifest? = try {
            val obj = JSONObject(json)
            val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
            val assets = buildList {
                for (i in 0 until assetsArr.length()) {
                    val a = assetsArr.optJSONObject(i) ?: continue
                    add(
                        UpdateAsset(
                            name = a.optString("name"),
                            downloadUrl = a.optString("downloadUrl"),
                            sizeBytes = a.optLong("sizeBytes", -1L),
                        ),
                    )
                }
            }
            UpdateManifest(
                tagName = obj.optString("tagName"),
                name = obj.optString("name"),
                body = obj.optString("body"),
                publishedAt = obj.optString("publishedAt"),
                assets = assets,
            )
        } catch (t: Throwable) {
            null
        }

        /**
         * Match `v<MAJOR>.<MINOR>.<PATCH>` and compute a monotonic
         * int that preserves release ordering across the Nira
         * release history. Also tolerant of a leading `release/`
         * segment (not currently used but cheap to accept).
         */
        fun parseVersionCodeFromTag(tag: String): Int? {
            val cleaned = tag.removePrefix("release/").removePrefix("v")
            val parts = cleaned.split('.')
            if (parts.size != 3) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            val patch = parts[2].toIntOrNull() ?: return null
            if (major < 0 || minor < 0 || patch < 0) return null
            if (minor > 999 || patch > 999) return null
            return major * 1_000_000 + minor * 1_000 + patch
        }
    }
}

/**
 * One file attached to a GitHub release. Nira release workflow
 * publishes one APK per ABI (`app-arm64-v8a-release.apk`,
 * `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`), so the
 * updater picks the asset whose [name] contains the current device's
 * primary ABI.
 */
internal data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("downloadUrl", downloadUrl)
        put("sizeBytes", sizeBytes)
    }
}
