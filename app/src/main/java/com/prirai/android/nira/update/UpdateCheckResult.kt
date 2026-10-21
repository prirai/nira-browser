package com.prirai.android.nira.update

/**
 * Outcome of a single call to [UpdateChecker.check]. Sealed so UI
 * code can exhaustively dispatch on it without resorting to
 * `when { manifest != null -> ... else -> ... }` style guards that
 * drop context.
 */
internal sealed class UpdateCheckResult {

    /**
     * The installed version is at or ahead of the latest published
     * release. Also returned when the check runs against a build
     * whose version code cannot be meaningfully compared (e.g. a
     * manually bumped local build) - the invariant is "do not
     * nag", not "no release exists".
     */
    object UpToDate : UpdateCheckResult()

    /**
     * A newer version is available and we have a usable asset for
     * the current device's ABI. [chosenAsset] points at the
     * specific APK the download step should pull.
     */
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val chosenAsset: UpdateAsset,
    ) : UpdateCheckResult()

    /** Network / parse / IO problem; details in [reason]. */
    data class Error(val reason: String) : UpdateCheckResult()

    /**
     * The current device reports an ABI that does not match any
     * published asset (e.g. x86 32-bit, or a riscv64 cross-compile).
     * Treat as a user-facing "update unavailable for this device"
     * state rather than a transient error.
     */
    data class UnsupportedAbi(val abi: String) : UpdateCheckResult()
}
