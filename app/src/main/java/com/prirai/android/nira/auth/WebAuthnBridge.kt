package com.prirai.android.nira.auth

import android.app.Activity
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge utilities for converting between WebAuthn API formats and Credential Manager formats.
 * Handles data transformation for passkey operations.
 */
object WebAuthnBridge {

    /**
     * Prepare a registration request for creating a passkey.
     * Ensures the request includes required fields for Credential Manager API.
     * 
     * @param options The PublicKeyCredentialCreationOptions from the website
     * @return Modified JSONObject with proper configuration for Credential Manager
     */
    fun prepareRegistrationRequest(options: JSONObject): JSONObject {
        val modifiedOptions = JSONObject(options.toString())
        
        // Ensure authenticatorSelection has required fields for passkeys
        if (!modifiedOptions.has("authenticatorSelection")) {
            modifiedOptions.put("authenticatorSelection", JSONObject())
        }
        
        val authenticatorSelection = modifiedOptions.getJSONObject("authenticatorSelection")
        
        // Set resident key to required for discoverable credentials (passkeys)
        if (!authenticatorSelection.has("residentKey")) {
            authenticatorSelection.put("residentKey", "required")
        }
        
        // Prefer platform authenticators (device biometrics)
        if (!authenticatorSelection.has("authenticatorAttachment")) {
            authenticatorSelection.put("authenticatorAttachment", "platform")
        }
        
        // Require user verification for security
        if (!authenticatorSelection.has("userVerification")) {
            authenticatorSelection.put("userVerification", "required")
        }
        
        modifiedOptions.put("authenticatorSelection", authenticatorSelection)
        
        // Set attestation to none for privacy
        if (!modifiedOptions.has("attestation")) {
            modifiedOptions.put("attestation", "none")
        }
        
        return modifiedOptions
    }

    /**
     * Prepare an authentication request for using a passkey.
     * 
     * @param options The PublicKeyCredentialRequestOptions from the website
     * @return Modified JSONObject with proper configuration for Credential Manager
     */
    fun prepareAuthenticationRequest(options: JSONObject): JSONObject {
        val modifiedOptions = JSONObject(options.toString())
        
        // Require user verification for security
        if (!modifiedOptions.has("userVerification")) {
            modifiedOptions.put("userVerification", "required")
        }
        
        return modifiedOptions
    }

    /**
     * Convert a registration response from Credential Manager to WebAuthn format.
     * 
     * @param credentialManagerResponse The response JSON from Credential Manager
     * @return JSONObject in WebAuthn PublicKeyCredential format
     */
    fun convertRegistrationResponse(credentialManagerResponse: JSONObject): JSONObject {
        // Credential Manager already returns data in WebAuthn format
        // This method can be extended for additional transformations if needed
        return credentialManagerResponse
    }

    /**
     * Convert an authentication response from Credential Manager to WebAuthn format.
     * 
     * @param credentialManagerResponse The response JSON from Credential Manager
     * @return JSONObject in WebAuthn PublicKeyCredential format
     */
    fun convertAuthenticationResponse(credentialManagerResponse: JSONObject): JSONObject {
        // Credential Manager already returns data in WebAuthn format
        // This method can be extended for additional transformations if needed
        return credentialManagerResponse
    }

    /**
     * Validate that a registration request contains all required fields.
     * 
     * @param options The PublicKeyCredentialCreationOptions to validate
     * @return true if valid, false otherwise
     */
    fun isValidRegistrationRequest(options: JSONObject): Boolean {
        return try {
            options.has("challenge") &&
            options.has("rp") &&
            options.has("user") &&
            options.has("pubKeyCredParams") &&
            options.getJSONObject("user").has("id") &&
            options.getJSONObject("user").has("name") &&
            options.getJSONObject("user").has("displayName")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate that an authentication request contains all required fields.
     * 
     * @param options The PublicKeyCredentialRequestOptions to validate
     * @return true if valid, false otherwise
     */
    fun isValidAuthenticationRequest(options: JSONObject): Boolean {
        return try {
            options.has("challenge") &&
            options.has("rpId")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract error information from a Credential Manager exception message.
     * 
     * @param exceptionMessage The exception message
     * @return A user-friendly error message
     */
    fun getErrorMessage(exceptionMessage: String?): String {
        return when {
            exceptionMessage == null -> "An unknown error occurred"
            exceptionMessage.contains("cancel", ignoreCase = true) -> 
                "Operation was cancelled by user"
            exceptionMessage.contains("timeout", ignoreCase = true) -> 
                "Operation timed out"
            exceptionMessage.contains("not found", ignoreCase = true) -> 
                "No passkey found for this site"
            exceptionMessage.contains("invalid", ignoreCase = true) -> 
                "Invalid passkey data"
            exceptionMessage.contains("security", ignoreCase = true) -> 
                "Security error occurred"
            else -> "Passkey operation failed: $exceptionMessage"
        }
    }
}
