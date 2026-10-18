package com.prirai.android.nira.auth

import android.app.Activity
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.log.logger.Logger

/**
 * Lifecycle-bound hook for passkey / WebAuthn flows.
 *
 * The actual WebAuthn credential UI is driven end-to-end by GeckoView's
 * built-in Credential Manager integration (`CredentialDelegate` /
 * `GeckoRuntimeSettings.setCredentialDelegate`), so this feature itself
 * does not have to proxy JS calls. It exists so a future per-site passkey
 * UI or custom credential picker can be hooked here without churning
 * `BaseBrowserFragment`.
 *
 * Previously carried `createPasskey` / `getPasskey` wrapper suspend
 * functions and a `CredentialManagerHelper` field; both were unreferenced
 * and have been removed. The feature is kept as a stub attachment point.
 */
class PasskeyAuthFeature(
    @Suppress("unused") private val engine: Engine,
    @Suppress("unused") private val activity: Activity,
    @Suppress("unused") private val store: BrowserStore,
    @Suppress("unused") private val onGetTabId: () -> String?,
) : LifecycleAwareFeature {

    private val logger = Logger("PasskeyAuthFeature")

    override fun start() {
        logger.debug("PasskeyAuthFeature started")
    }

    override fun stop() {
        logger.debug("PasskeyAuthFeature stopped")
    }
}
