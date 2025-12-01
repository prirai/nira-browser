package com.prirai.android.nira.auth

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Example usage of Passkey authentication with Credential Manager.
 * This file demonstrates how to manually trigger passkey operations.
 * 
 * NOTE: In most cases, passkey operations are handled automatically by GeckoView
 * when websites call navigator.credentials.create() or navigator.credentials.get()
 */
object PasskeyUsageExample {

    /**
     * Example: Register a new passkey for a user
     * 
     * This would typically be called when:
     * - User clicks "Sign up with passkey" button
     * - Server provides PublicKeyCredentialCreationOptions
     */
    suspend fun registerPasskeyExample(
        activity: Activity,
        scope: CoroutineScope
    ) {
        val helper = CredentialManagerHelper(activity)
        
        // Step 1: Get registration options from your server
        // This is a mock example - in reality, this comes from your backend
        val registrationOptions = JSONObject().apply {
            put("challenge", "base64EncodedChallenge")
            put("rp", JSONObject().apply {
                put("name", "Example App")
                put("id", "example.com")
            })
            put("user", JSONObject().apply {
                put("id", "base64EncodedUserId")
                put("name", "user@example.com")
                put("displayName", "Example User")
            })
            put("pubKeyCredParams", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "public-key")
                    put("alg", -7) // ES256
                })
            })
            put("timeout", 60000)
            put("authenticatorSelection", JSONObject().apply {
                put("authenticatorAttachment", "platform")
                put("requireResidentKey", true)
                put("residentKey", "required")
                put("userVerification", "required")
            })
            put("attestation", "none")
        }
        
        // Step 2: Validate and prepare the request
        if (!WebAuthnBridge.isValidRegistrationRequest(registrationOptions)) {
            // Handle invalid request
            return
        }
        
        val preparedRequest = WebAuthnBridge.prepareRegistrationRequest(registrationOptions)
        
        // Step 3: Create the passkey
        scope.launch {
            val response = helper.createPasskey(activity, preparedRequest)
            
            if (response != null) {
                // Step 4: Parse the response
                val responseJson = helper.parseCreatePasskeyResponse(response)
                
                // Step 5: Send responseJson to your server for verification
                // sendToServer("/register/verify", responseJson)
                
                println("Passkey created successfully!")
                println("Response: $responseJson")
            } else {
                println("Passkey creation failed")
            }
        }
    }

    /**
     * Example: Authenticate with an existing passkey
     * 
     * This would typically be called when:
     * - User clicks "Sign in with passkey" button
     * - Server provides PublicKeyCredentialRequestOptions
     */
    suspend fun authenticatePasskeyExample(
        activity: Activity,
        scope: CoroutineScope
    ) {
        val helper = CredentialManagerHelper(activity)
        
        // Step 1: Get authentication options from your server
        // This is a mock example - in reality, this comes from your backend
        val authenticationOptions = JSONObject().apply {
            put("challenge", "base64EncodedChallenge")
            put("timeout", 60000)
            put("rpId", "example.com")
            put("userVerification", "required")
            // allowCredentials can be empty for discoverable credentials
            put("allowCredentials", org.json.JSONArray())
        }
        
        // Step 2: Validate and prepare the request
        if (!WebAuthnBridge.isValidAuthenticationRequest(authenticationOptions)) {
            // Handle invalid request
            return
        }
        
        val preparedRequest = WebAuthnBridge.prepareAuthenticationRequest(authenticationOptions)
        
        // Step 3: Get the passkey
        scope.launch {
            val response = helper.getPasskey(activity, preparedRequest)
            
            if (response != null) {
                // Step 4: Parse the response
                val responseJson = helper.parseGetPasskeyResponse(response)
                
                if (responseJson != null) {
                    // Step 5: Send responseJson to your server for verification
                    // sendToServer("/authenticate/verify", responseJson)
                    
                    println("Authentication successful!")
                    println("Response: $responseJson")
                } else {
                    println("Response was not a passkey credential")
                }
            } else {
                println("Authentication failed")
            }
        }
    }

    /**
     * Example: Check if passkeys are supported before showing UI
     */
    fun checkPasskeySupport(activity: Activity): Boolean {
        val helper = CredentialManagerHelper(activity)
        val isSupported = helper.isPasskeySupported(activity)
        
        if (isSupported) {
            println("✓ Passkeys are supported on this device")
        } else {
            println("✗ Passkeys are not supported on this device")
        }
        
        return isSupported
    }

    /**
     * Example: Handle errors gracefully
     */
    suspend fun authenticateWithErrorHandling(
        activity: Activity,
        scope: CoroutineScope,
        authOptions: JSONObject
    ) {
        val helper = CredentialManagerHelper(activity)
        
        scope.launch {
            try {
                val response = helper.getPasskey(activity, authOptions)
                
                if (response != null) {
                    val responseJson = helper.parseGetPasskeyResponse(response)
                    // Success - send to server
                    println("Success: $responseJson")
                } else {
                    // User cancelled or error occurred
                    // Check logs for specific error message
                    println("Authentication cancelled or failed")
                }
            } catch (e: Exception) {
                // Handle unexpected errors
                val userMessage = WebAuthnBridge.getErrorMessage(e.message)
                println("Error: $userMessage")
                // Show error to user via UI
            }
        }
    }
}
