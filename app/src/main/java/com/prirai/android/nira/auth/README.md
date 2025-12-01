# Credential Manager Integration for Passkey Support

This package contains the implementation of Android's Credential Manager API for passkey authentication, replacing the older FIDO2/WebAuthn implementation.

## Files

### CredentialManagerHelper.kt
Core helper class that wraps the Android Credential Manager API:
- `createPasskey()`: Creates a new passkey credential
- `getPasskey()`: Retrieves and authenticates with an existing passkey
- Error handling for all credential operations
- Support for both passkeys and traditional passwords

### PasskeyAuthFeature.kt
Feature implementation that integrates with the browser's lifecycle:
- Provides lifecycle-aware passkey operations
- Integrates with Mozilla's BrowserStore
- Maintains compatibility with the browser's feature architecture

## Migration from FIDO2 to Credential Manager

This implementation follows Google's recommended migration path:

### Key Changes:
1. **Updated Dependencies** (app/build.gradle):
   - `androidx.credentials:credentials:1.3.0`
   - `androidx.credentials:credentials-play-services-auth:1.3.0`
   - `com.google.android.gms:play-services-auth:21.2.0`

2. **Replaced WebAuthnFeature** (BaseBrowserFragment.kt):
   - Old: `mozilla.components.feature.webauthn.WebAuthnFeature`
   - New: `com.prirai.android.nira.auth.PasskeyAuthFeature`

3. **Modern API Usage**:
   - Uses `CredentialManager.create()` for initialization
   - `CreatePublicKeyCredentialRequest` for passkey creation
   - `GetPublicKeyCredentialOption` for passkey retrieval
   - Supports multiple sign-in methods (passkeys + passwords)

## Benefits

- **Passkey Support**: Fully supports modern passkey authentication
- **Multiple Sign-in Methods**: Supports passkeys, passwords, and federated sign-in
- **Third-party Provider Support**: Works with credential providers on Android 14+
- **Consistent UX**: Provides unified authentication experience across apps
- **Backward Compatibility**: Works on Android 9+ via Play Services

## Usage

The PasskeyAuthFeature is automatically initialized in BaseBrowserFragment and handles WebAuthn requests from websites transparently. No additional code is needed for basic passkey support.

For advanced usage, access the feature via:
```kotlin
passkeyAuthFeature.get()?.createPasskey(requestJson)
passkeyAuthFeature.get()?.getPasskey(requestJson)
```

## Browser Integration

GeckoView (Mozilla's browser engine) has built-in support for the Credential Manager API starting from GeckoView 120+. The PasskeyAuthFeature provides:
1. Additional helper methods for manual credential operations
2. Logging and debugging capabilities
3. Error handling and user feedback

The actual WebAuthn credential operations are handled automatically by GeckoView when websites call:
- `navigator.credentials.create()` - for passkey registration
- `navigator.credentials.get()` - for passkey authentication

## Notes

- **Resident Keys**: The implementation uses discoverable credentials (resident keys) by setting `residentKey: "required"` in the authenticatorSelection options
- **User Verification**: Set to "required" for enhanced security
- **Platform Authenticators**: Prefers platform authenticators (device biometrics) over cross-platform authenticators
