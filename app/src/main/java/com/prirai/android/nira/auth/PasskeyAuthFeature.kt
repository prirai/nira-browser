package com.prirai.android.nira.auth

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONObject

/**
 * Feature implementation for Passkey authentication using Credential Manager API.
 * This replaces the Mozilla WebAuthnFeature with modern Android Credential Manager.
 * 
 * @property engine The browser engine
 * @property activity The activity for displaying credential UI
 * @property store The browser store for state management
 * @property onGetTabId Callback to get the current tab ID
 */
class PasskeyAuthFeature(
    private val engine: Engine,
    private val activity: Activity,
    private val store: BrowserStore,
    private val onGetTabId: () -> String?
) : LifecycleAwareFeature {

    private val logger = Logger("PasskeyAuthFeature")
    private val credentialHelper = CredentialManagerHelper(activity.applicationContext)
    
    init {
        logger.info("PasskeyAuthFeature initialized with Credential Manager API")
    }

    /**
     * Start observing for passkey requests from web content.
     * This integrates with the browser engine's WebAuthn API.
     */
    override fun start() {
        logger.debug("PasskeyAuthFeature started")
        // Note: Actual WebAuthn integration happens through GeckoView's built-in
        // credential manager support. This feature provides additional helper methods.
    }

    override fun stop() {
        logger.debug("PasskeyAuthFeature stopped")
    }

    /**
     * Create a passkey for the current website.
     * This should be called when a website requests credential creation via navigator.credentials.create()
     * 
     * @param requestJson The PublicKeyCredentialCreationOptions from the website
     * @return The registration response JSON to send back to the website
     */
    suspend fun createPasskey(requestJson: JSONObject): JSONObject? {
        logger.info("Creating passkey for current tab")
        
        if (!credentialHelper.isPasskeySupported(activity)) {
            logger.warn("Passkeys not supported on this device")
            return null
        }
        
        val response = credentialHelper.createPasskey(activity, requestJson)
        
        return response?.let { 
            credentialHelper.parseCreatePasskeyResponse(it)
        }
    }

    /**
     * Get/authenticate with an existing passkey.
     * This should be called when a website requests credential authentication via navigator.credentials.get()
     * 
     * @param requestJson The PublicKeyCredentialRequestOptions from the website
     * @return The authentication response JSON to send back to the website
     */
    suspend fun getPasskey(requestJson: JSONObject): JSONObject? {
        logger.info("Getting passkey for current tab")
        
        if (!credentialHelper.isPasskeySupported(activity)) {
            logger.warn("Passkeys not supported on this device")
            return null
        }
        
        val response = credentialHelper.getPasskey(activity, requestJson)
        
        return response?.let {
            credentialHelper.parseGetPasskeyResponse(it)
        }
    }

    companion object {
        private const val TAG = "PasskeyAuthFeature"
    }
}
