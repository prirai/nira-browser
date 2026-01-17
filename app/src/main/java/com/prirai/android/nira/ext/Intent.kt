package com.prirai.android.nira.ext

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import mozilla.components.feature.intent.ext.getSessionId
import mozilla.components.support.utils.SafeIntent

inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key)
    }
}

inline fun <reified T : Parcelable> Intent.getParcelableArrayListExtraCompat(key: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, T::class.java)
    } else {
        getParcelableArrayListExtra(key)
    }
}

/**
 * Returns the session ID from the intent, supporting custom tabs.
 */
fun getIntentSessionId(intent: SafeIntent): String? {
    return intent.getSessionId()
}
