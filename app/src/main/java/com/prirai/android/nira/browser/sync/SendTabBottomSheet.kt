package com.prirai.android.nira.browser.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.launch
import mozilla.components.concept.sync.Device
import mozilla.components.concept.sync.DeviceCommandOutgoing
import mozilla.components.concept.sync.DeviceType
import mozilla.components.service.fxa.manager.FxaAccountManager

class SendTabBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SendTabBottomSheet"
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"

        fun newInstance(url: String, title: String): SendTabBottomSheetFragment {
            return SendTabBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.AppBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = androidx.compose.ui.platform.LocalContext.current
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors
                ) {
                    SendTabBottomSheet(
                        url = arguments?.getString(ARG_URL) ?: "",
                        title = arguments?.getString(ARG_TITLE) ?: "",
                        accountManager = context.components.fxaSyncManager.accountManager,
                        onDismiss = { dismiss() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendTabBottomSheet(
    url: String,
    title: String,
    accountManager: FxaAccountManager,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var devices by remember { mutableStateOf<List<Device>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val constellation = accountManager.authenticatedAccount()?.deviceConstellation()
        constellation?.refreshDevices()
        devices = constellation?.state()?.otherDevices ?: emptyList()
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        ListItem(
            headlineContent = {
                Text(text = "Send tab to\u2026")
            }
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            }

            devices.isNullOrEmpty() -> {
                ListItem(
                    headlineContent = {
                        Text(text = "No other devices signed in to this Firefox Account")
                    }
                )
            }

            else -> {
                LazyColumn {
                    items(devices!!) { device ->
                        ListItem(
                            headlineContent = { Text(text = device.displayName) },
                            leadingContent = {
                                Icon(
                                    imageVector = when (device.deviceType) {
                                        DeviceType.DESKTOP -> Icons.Rounded.Computer
                                        DeviceType.TABLET -> Icons.Rounded.Tablet
                                        else -> Icons.Rounded.PhoneAndroid
                                    },
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send"
                                )
                            },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    accountManager.authenticatedAccount()
                                        ?.deviceConstellation()
                                        ?.sendCommandToDevice(
                                            device.id,
                                            DeviceCommandOutgoing.SendTab(title, url)
                                        )
                                    snackbarHostState.showSnackbar("Tab sent!")
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
