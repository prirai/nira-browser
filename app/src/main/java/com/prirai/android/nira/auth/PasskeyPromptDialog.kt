package com.prirai.android.nira.auth

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.prirai.android.nira.R

/**
 * Dialog for prompting users about passkey operations.
 * Provides user-friendly messaging for passkey creation and authentication.
 */
class PasskeyPromptDialog : DialogFragment() {

    private var onPositiveClick: (() -> Unit)? = null
    private var onNegativeClick: (() -> Unit)? = null
    private var dialogTitle: String = ""
    private var dialogMessage: String = ""
    private var positiveButtonText: String = ""
    private var negativeButtonText: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_edittext, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // This is a placeholder - in a real implementation, you'd create a custom layout
        // for passkey prompts with proper UI elements
    }

    companion object {
        private const val TAG = "PasskeyPromptDialog"
        
        /**
         * Create a dialog for passkey creation confirmation.
         */
        fun forCreation(
            siteName: String,
            userName: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit
        ): PasskeyPromptDialog {
            return PasskeyPromptDialog().apply {
                dialogTitle = "Create a Passkey"
                dialogMessage = "Create a passkey for $userName on $siteName?\n\n" +
                    "You'll be able to sign in using your fingerprint, face, or screen lock."
                positiveButtonText = "Create"
                negativeButtonText = "Cancel"
                onPositiveClick = onConfirm
                onNegativeClick = onCancel
            }
        }
        
        /**
         * Create a dialog for passkey authentication confirmation.
         */
        fun forAuthentication(
            siteName: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit
        ): PasskeyPromptDialog {
            return PasskeyPromptDialog().apply {
                dialogTitle = "Sign In with Passkey"
                dialogMessage = "Use your passkey to sign in to $siteName?"
                positiveButtonText = "Sign In"
                negativeButtonText = "Cancel"
                onPositiveClick = onConfirm
                onNegativeClick = onCancel
            }
        }
        
        /**
         * Create an error dialog for passkey operations.
         */
        fun forError(
            errorMessage: String,
            onDismiss: () -> Unit
        ): PasskeyPromptDialog {
            return PasskeyPromptDialog().apply {
                dialogTitle = "Passkey Error"
                dialogMessage = errorMessage
                positiveButtonText = "OK"
                negativeButtonText = ""
                onPositiveClick = onDismiss
                onNegativeClick = onDismiss
            }
        }
    }
}
