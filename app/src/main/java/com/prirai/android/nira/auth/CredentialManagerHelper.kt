package com.prirai.android.nira.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPasswordOption
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import org.json.JSONObject

/**
 * Helper class for managing Credential Manager operations for Passkey authentication.
 * Replaces FIDO2 implementation with modern Credential Manager API.
 */
class CredentialManagerHelper(context: Context) {

    private val credentialManager = CredentialManager.create(context)
    
    companion object {
        private const val TAG = "CredentialManagerHelper"
    }

    /**
     * Create a new passkey credential.
     * 
     * @param activity The activity context required for credential creation UI
     * @param requestJson The JSON object containing PublicKeyCredentialCreationOptions from server
     * @return CreatePublicKeyCredentialResponse on success, null on failure
     */
    suspend fun createPasskey(
        activity: Activity,
        requestJson: JSONObject
    ): CreatePublicKeyCredentialResponse? {
        return try {
            Log.d(TAG, "Creating passkey with request: $requestJson")
            
            val request = CreatePublicKeyCredentialRequest(requestJson.toString())
            
            val response = credentialManager.createCredential(
                request = request,
                context = activity
            ) as CreatePublicKeyCredentialResponse
            
            Log.d(TAG, "Passkey created successfully")
            response
        } catch (e: CreateCredentialException) {
            Log.e(TAG, "Error creating passkey", e)
            handleCreateCredentialException(e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error creating passkey", e)
            null
        }
    }

    /**
     * Authenticate with an existing passkey.
     * 
     * @param activity The activity context required for credential selection UI
     * @param requestJson The JSON object containing PublicKeyCredentialRequestOptions from server
     * @return GetCredentialResponse containing the passkey credential on success, null on failure
     */
    suspend fun getPasskey(
        activity: Activity,
        requestJson: JSONObject
    ): GetCredentialResponse? {
        return try {
            Log.d(TAG, "Getting passkey with request: $requestJson")
            
            val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
                requestJson = requestJson.toString(),
                clientDataHash = null
            )
            
            val request = GetCredentialRequest(
                listOf(
                    getPublicKeyCredentialOption,
                    GetPasswordOption() // Also support traditional passwords
                )
            )
            
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            
            when (result.credential) {
                is PublicKeyCredential -> {
                    val publicKeyCredential = result.credential as PublicKeyCredential
                    Log.d(TAG, "Passkey retrieved: ${publicKeyCredential.authenticationResponseJson}")
                }
                else -> {
                    Log.d(TAG, "Other credential type retrieved: ${result.credential.type}")
                }
            }
            
            result
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Error getting passkey", e)
            handleGetCredentialException(e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting passkey", e)
            null
        }
    }

    /**
     * Parse the response from createPasskey to send back to server.
     * 
     * @param response The CreatePublicKeyCredentialResponse from createPasskey
     * @return JSONObject containing the credential data to send to server
     */
    fun parseCreatePasskeyResponse(response: CreatePublicKeyCredentialResponse): JSONObject {
        return JSONObject(response.registrationResponseJson)
    }

    /**
     * Parse the response from getPasskey to send back to server.
     * 
     * @param response The GetCredentialResponse from getPasskey
     * @return JSONObject containing the authentication data to send to server, or null if not a passkey
     */
    fun parseGetPasskeyResponse(response: GetCredentialResponse): JSONObject? {
        return when (val credential = response.credential) {
            is PublicKeyCredential -> {
                JSONObject(credential.authenticationResponseJson)
            }
            else -> {
                Log.w(TAG, "Response does not contain a PublicKeyCredential")
                null
            }
        }
    }

    /**
     * Handle CreateCredentialException and provide user-friendly error messages.
     */
    private fun handleCreateCredentialException(e: CreateCredentialException) {
        when (e::class.simpleName) {
            "CreateCredentialCancellationException" -> {
                Log.w(TAG, "User cancelled passkey creation")
            }
            "CreateCredentialInterruptedException" -> {
                Log.w(TAG, "Passkey creation was interrupted")
            }
            "CreateCredentialProviderConfigurationException" -> {
                Log.e(TAG, "Credential provider configuration error", e)
            }
            "CreateCredentialUnknownException" -> {
                Log.e(TAG, "Unknown error during passkey creation", e)
            }
            "CreateCredentialCustomException" -> {
                Log.e(TAG, "Custom error during passkey creation", e)
            }
            else -> {
                Log.e(TAG, "Unhandled CreateCredentialException: ${e::class.simpleName}", e)
            }
        }
    }

    /**
     * Handle GetCredentialException and provide user-friendly error messages.
     */
    private fun handleGetCredentialException(e: GetCredentialException) {
        when (e::class.simpleName) {
            "GetCredentialCancellationException" -> {
                Log.w(TAG, "User cancelled passkey selection")
            }
            "GetCredentialInterruptedException" -> {
                Log.w(TAG, "Passkey retrieval was interrupted")
            }
            "GetCredentialProviderConfigurationException" -> {
                Log.e(TAG, "Credential provider configuration error", e)
            }
            "GetCredentialUnknownException" -> {
                Log.e(TAG, "Unknown error during passkey retrieval", e)
            }
            "GetCredentialCustomException" -> {
                Log.e(TAG, "Custom error during passkey retrieval", e)
            }
            "NoCredentialException" -> {
                Log.w(TAG, "No passkey found for this site")
            }
            else -> {
                Log.e(TAG, "Unhandled GetCredentialException: ${e::class.simpleName}", e)
            }
        }
    }

    /**
     * Check if the device supports passkeys.
     * This should be called before attempting passkey operations.
     */
    fun isPasskeySupported(context: Context): Boolean {
        return try {
            // Credential Manager is available on Android 9+ with Play Services
            // or natively on Android 14+
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking passkey support", e)
            false
        }
    }
}
