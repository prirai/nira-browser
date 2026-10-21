package com.prirai.android.nira.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands the downloaded APK off to the system package installer.
 *
 * The actual bytes-to-install flow is Android's responsibility - we
 * only grant it a FileProvider URI and dispatch an
 * [Intent.ACTION_VIEW]. The system installer then:
 *   1. Verifies the APK signature against the currently-installed
 *      Nira signature. A mismatch (e.g. the user flashed a release
 *      build after sideloading a debug build) is rejected with a
 *      visible error - that is the correct failure mode.
 *   2. Shows the "Install an update to this app" prompt, including
 *      the version diff and permissions delta.
 *   3. Rolls the APK into the installed app on tap.
 *
 * We don't try to be clever with [PackageInstaller] session-based
 * installs: the ACTION_VIEW path works on every supported Android
 * version, keeps the OS in charge of the trust decision, and
 * requires no `UPDATE_PACKAGES_WITHOUT_USER_ACTION` manifest flag
 * (which is restricted to installer packages like Play Store).
 *
 * # Required manifest additions
 *   - `android.permission.REQUEST_INSTALL_PACKAGES`
 *   - a FileProvider authority of `${applicationId}.updateprovider`
 *     scoped to the updates directory in cacheDir
 *
 * See branch 10's manifest edits.
 */
internal object UpdateInstaller {

    private const val TAG = "NiraUpdateInstall"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /**
     * Launch the system installer for [apk]. Returns `true` if the
     * intent was dispatched (the install UI should appear), `false`
     * if we could not resolve a FileProvider or the intent found
     * no activity to handle it (very unusual - every stock Android
     * ships a package installer).
     */
    fun installApk(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() <= 0) {
            Log.w(TAG, "install aborted: apk missing or empty at ${apk.path}")
            return false
        }

        val authority = "${context.packageName}.updateprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(context, authority, apk)
        } catch (t: Throwable) {
            Log.w(TAG, "FileProvider.getUriForFile failed for $authority / ${apk.path}", t)
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        val resolved = intent.resolveActivity(context.packageManager)
        if (resolved == null) {
            Log.w(TAG, "no installer activity found to handle ACTION_VIEW on $uri")
            return false
        }

        return try {
            context.startActivity(intent)
            Log.i(TAG, "install intent dispatched for ${apk.name}")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "startActivity for installer failed", t)
            false
        }
    }
}
