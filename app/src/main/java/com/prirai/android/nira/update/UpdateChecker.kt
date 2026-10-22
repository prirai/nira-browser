package com.prirai.android.nira.update

import android.content.Context
import android.os.Build
import android.util.Log
import com.prirai.android.nira.BuildConfig
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request

/**
 * Entry point for a single "is there a newer release?" probe.
 *
 * Not a long-lived object; call [check] from a coroutine, inspect the
 * [UpdateCheckResult], react. All work is pushed onto `Dispatchers.IO`
 * - callers can safely invoke this from Main.
 *
 * # Trust model
 *   - Transport: HTTPS to `api.github.com` and the asset URL under
 *     `github.com`. HSTS-eligible hostnames, no cert pinning (first-
 *     party trust is fine for this use case).
 *   - Payload: the Android package installer verifies the APK's
 *     signature against the installed signature before replacing the
 *     app. A tampered APK is rejected by the OS, not by this code.
 *     We don't need to ship a public key or embed a checksum.
 *   - Rollback: [UpdateChecker] only reports an update when the
 *     fetched manifest's version is **strictly greater** than
 *     [BuildConfig.VERSION_CODE]. A compromised GitHub token
 *     cannot be used to force a downgrade.
 *
 * # Release-only guard
 *   The checker is **always** a no-op on debug builds. The release
 *   APK signature does not match the debug one, so letting a dev
 *   device run the install flow would reliably fail with a user-
 *   visible error. See [checkInternal] early-return.
 */
internal object UpdateChecker {

    private const val TAG = "NiraUpdateCheck"
    private const val USER_AGENT = "Nira-browser-update-checker"

    /**
     * Fetch the manifest for [channel] and compare it against the
     * installed version. Returns a sealed [UpdateCheckResult] - never
     * throws.
     */
    suspend fun check(
        context: Context,
        channel: UpdateChannel = UpdateChannel.STABLE,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) {
            return@withContext UpdateCheckResult.UpToDate
        }
        checkInternal(context, channel)
    }

    private suspend fun checkInternal(
        context: Context,
        channel: UpdateChannel,
    ): UpdateCheckResult {
        val headers = MutableHeaders().apply {
            // Explicit API version header pins the response schema;
            // without it GitHub may serve a newer representation that
            // drops fields we rely on (currently stable but cheap
            // insurance).
            append("Accept", "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2022-11-28")
            append("User-Agent", USER_AGENT)
        }
        val request = Request(
            url = channel.releasesApiUrl,
            headers = headers,
            method = Request.Method.GET,
            // We never want a cached stale response here.
            useCaches = false,
        )

        val body: String = try {
            context.components.client.fetch(request).use { response ->
                if (response.status !in 200..299) {
                    return UpdateCheckResult.Error("HTTP ${response.status}")
                }
                response.body.string(Charsets.UTF_8)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "fetch failed", t)
            return UpdateCheckResult.Error(t.message ?: "network error")
        }

        val manifest = UpdateManifest.fromReleaseJson(body)
            ?: return UpdateCheckResult.Error("manifest parse failed")

        val remoteVersion = manifest.versionCode
            ?: return UpdateCheckResult.Error("unparseable tag: ${manifest.tagName}")

        val localVersion = BuildConfig.VERSION_CODE
        // The versionCode arithmetic in UpdateManifest.parseVersionCodeFromTag
        // intentionally differs from the one in app/build.gradle - comparing
        // *tag-derived* numbers is only meaningful between manifests. Compare
        // the installed version against a tag-derived number for the SAME
        // tag by re-parsing the versionName.
        val localTagVersion = UpdateManifest.parseVersionCodeFromTag(
            BuildConfig.VERSION_NAME,
        ) ?: run {
            Log.w(TAG, "could not parse local versionName ${BuildConfig.VERSION_NAME}")
            return UpdateCheckResult.Error("local versionName unparseable")
        }

        if (remoteVersion <= localTagVersion) {
            Log.i(TAG, "up to date (local=$localTagVersion remote=$remoteVersion)")
            return UpdateCheckResult.UpToDate
        }

        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        val asset = pickAssetForAbi(manifest, primaryAbi)
            ?: return UpdateCheckResult.UnsupportedAbi(primaryAbi)

        Log.i(
            TAG,
            "update available: ${manifest.tagName} (${asset.name}, ${asset.sizeBytes}B) local=$localTagVersion remote=$remoteVersion",
        )
        return UpdateCheckResult.UpdateAvailable(manifest = manifest, chosenAsset = asset)
    }

    /**
     * Match the asset filename against the device's primary ABI.
     *
     * Nira's release workflow currently publishes exactly three
     * APKs: `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`,
     * `app-x86_64-release.apk`. We match on substring so a future
     * workflow change that adds a prefix / suffix stays compatible,
     * and we require the asset to end in `.apk` so we never pick
     * up a `.asc` signature file or similar.
     *
     * Returns `null` when the device ABI is not one of the three
     * published - caller surfaces this as [UpdateCheckResult.UnsupportedAbi].
     */
    private fun pickAssetForAbi(
        manifest: UpdateManifest,
        abi: String,
    ): UpdateAsset? {
        val wanted = when (abi) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            "x86_64" -> "x86_64"
            else -> return null
        }
        return manifest.assets.firstOrNull {
            it.name.contains(wanted) && it.name.endsWith(".apk", ignoreCase = true)
        }
    }
}
