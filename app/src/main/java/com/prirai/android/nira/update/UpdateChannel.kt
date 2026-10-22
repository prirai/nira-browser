package com.prirai.android.nira.update

/**
 * Update distribution channel.
 *
 * Nira currently only ships through GitHub Releases on a single
 * stable channel. The enum exists to keep the public API shape
 * consistent in case we ever add a beta / nightly channel; it also
 * keeps the GitHub Releases API URL out of calling code so a future
 * channel switch is a one-line change here.
 *
 * Deliberately not exposing [BETA] or [NIGHTLY] yet - adding them is
 * a conscious decision, not something a user can trip into via a
 * typo'd pref value.
 */
internal enum class UpdateChannel(val releasesApiUrl: String) {
    /**
     * Standard tagged releases from `github.com/prirai/nira-browser`.
     *
     * Uses GitHub's `/releases/latest` endpoint, which returns the
     * most recent **non-prerelease** published release. If the Nira
     * release workflow ever publishes a release with
     * `prerelease=true`, that release is intentionally invisible to
     * the auto-updater on this channel.
     */
    STABLE("https://api.github.com/repos/prirai/nira-browser/releases/latest"),
}
