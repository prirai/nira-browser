@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.privacy.cookies.CookiePolicy
import com.prirai.android.nira.privacy.cookies.CookiePolicyRepository
import com.prirai.android.nira.ssl.SitePermissionsBottomSheet
import com.prirai.android.nira.theme.ThemeManager
import com.prirai.android.nira.ui.components.NiraAlertDialog
import com.prirai.android.nira.ui.components.NiraCardRow
import com.prirai.android.nira.ui.components.NiraEmptyState
import com.prirai.android.nira.ui.components.NiraLoadingIndicator
import com.prirai.android.nira.ui.components.animateNiraListItem
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissions.AutoplayStatus
import mozilla.components.concept.engine.permission.SitePermissions.Status

/**
 * Settings sub-screen that lists every origin with a per-site exception
 * (either an AC `SitePermissions` row with any non-default field, or a
 * Nira cookie-retention policy, or both) and lets the user tap through
 * to the full [SitePermissionsBottomSheet] for that origin.
 *
 * The screen is deliberately read-mostly: there is no inline edit. The
 * only destructive action here is "Clear all exceptions", which wipes
 * both the AC permission storage (`permissionStorage.removeAll()`) and
 * the local cookie-policy table.
 *
 * Rows are grouped by origin (one row per unique origin, union of the
 * two sources). A row shows the origin, a one-line summary describing
 * which permissions deviate from defaults and whether a cookie-retention
 * policy exists, and a trailing "remove" button that drops this origin's
 * AC row and cookie policy (without touching other origins).
 */
class SiteExceptionsFragment : Fragment() {

    /** null while a (re)load is in flight — the loading indicator's slot. */
    private var rows by mutableStateOf<List<ExceptionRow>?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val prefs = UserPreferences(context)
                NiraTheme(
                    darkTheme = ThemeManager.isDarkMode(context),
                    amoledMode = prefs.amoledMode,
                    dynamicColor = prefs.dynamicColors,
                ) {
                    SiteExceptionsScreen(
                        rows = rows,
                        onBack = { parentFragmentManager.popBackStack() },
                        onOpenOrigin = { origin -> openSheetFor(origin) },
                        onRemoveOrigin = { origin -> removeExceptionsFor(origin) },
                        onClearAll = { clearAll() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload unconditionally: picks up any changes made via the
        // bottom sheet while this screen was on the back stack, too.
        reload()
    }

    private fun reload() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val perms = runCatching {
                    context.components.permissionStorage.all()
                }.getOrElse { emptyList() }
                val policies = CookiePolicyRepository.getInstance(context).getAll()

                buildRows(perms, policies)
            }
            if (!isAdded) return@launch
            rows = loaded
        }
    }

    private fun openSheetFor(origin: String) {
        SitePermissionsBottomSheet.newInstanceForOrigin(origin)
            .show(parentFragmentManager, SitePermissionsBottomSheet.TAG)
    }

    private fun removeExceptionsFor(origin: String) {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Remove the SitePermissions row if it exists.
                runCatching {
                    val row = context.components.permissionStorage
                        .findSitePermissionsBy(origin, private = false)
                    if (row != null) {
                        context.components.permissionStorage.remove(row, private = false)
                    }
                }
                // Remove the Nira cookie-retention policy.
                CookiePolicyRepository.getInstance(context).remove(origin)
            }
            reload()
        }
    }

    private fun clearAll() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.components.permissionStorage.removeAll() }
                CookiePolicyRepository.getInstance(context).removeAll()
            }
            reload()
        }
    }

    /**
     * Build one [ExceptionRow] per unique origin by unioning
     * `permissionStorage.all()` with the Nira cookie-policy rows. The
     * resulting list is sorted alphabetically for stability across
     * reloads.
     */
    private fun buildRows(
        permissions: List<SitePermissions>,
        policies: List<CookiePolicy>,
    ): List<ExceptionRow> {
        val byOrigin = mutableMapOf<String, Pair<SitePermissions?, CookiePolicy?>>()
        permissions.forEach { byOrigin[it.origin] = it to byOrigin[it.origin]?.second }
        policies.forEach { policy ->
            val existing = byOrigin[policy.origin]
            byOrigin[policy.origin] = (existing?.first) to policy
        }
        return byOrigin.entries
            .sortedBy { it.key }
            .map { (origin, pair) ->
                ExceptionRow(
                    origin = origin,
                    summary = summaryFor(pair.first, pair.second),
                )
            }
    }

    private fun summaryFor(perms: SitePermissions?, policy: CookiePolicy?): String {
        val parts = mutableListOf<String>()
        if (perms != null) {
            val count = perms.nonDefaultCount()
            if (count > 0) {
                parts += resources.getQuantityString(
                    R.plurals.site_exceptions_permissions_count,
                    count,
                    count,
                )
            }
        }
        if (policy != null) {
            parts += getString(
                R.string.site_exceptions_cookie_window,
                formatHours(policy.hoursUntilClear),
            )
        }
        return if (parts.isEmpty()) getString(R.string.site_exceptions_no_overrides)
        else parts.joinToString(" · ")
    }

    private fun formatHours(hours: Int): String {
        return if (hours >= 24 && hours % 24 == 0) {
            getString(R.string.cookie_retention_days, hours / 24)
        } else {
            getString(R.string.cookie_retention_hours, hours)
        }
    }
}

/** One origin's exceptions: the origin plus a one-line summary of what deviates from defaults. */
private data class ExceptionRow(val origin: String, val summary: String)

/**
 * The Site Exceptions screen on the shared Material 3 Expressive stack:
 * an M3 top bar with the back arrow and the clear-all action (the old
 * toolbar overflow item), then a grouped [NiraCardRow] list per origin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteExceptionsScreen(
    rows: List<ExceptionRow>?,
    onBack: () -> Unit,
    onOpenOrigin: (String) -> Unit,
    onRemoveOrigin: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearAll by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_exceptions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearAll = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.site_exceptions_clear_all),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            rows == null -> NiraLoadingIndicator(modifier = Modifier.padding(padding))
            rows.isEmpty() -> NiraEmptyState(
                title = stringResource(R.string.site_exceptions_empty),
                icon = Icons.Rounded.Shield,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> "exception-${row.origin}" },
                ) { index, row ->
                    SiteExceptionCardRow(
                        row = row,
                        isTopRow = index == 0,
                        isBottomRow = index == rows.lastIndex,
                        onClick = { onOpenOrigin(row.origin) },
                        onRemove = { onRemoveOrigin(row.origin) },
                        modifier = Modifier.then(animateNiraListItem()),
                    )
                }
            }
        }
    }

    if (showClearAll) {
        NiraAlertDialog(
            onDismissRequest = { showClearAll = false },
            title = {
                Text(
                    text = stringResource(R.string.site_exceptions_clear_all),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            },
            text = { Text(stringResource(R.string.site_exceptions_clear_all_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAll = false
                        onClearAll()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.site_exceptions_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * One origin on the shared grouped-card idiom. The summary of what
 * deviates from defaults travels in the trailing slot; tapping the row
 * opens the full permission sheet, the trailing close icon removes this
 * origin's exceptions only.
 */
@Composable
private fun SiteExceptionCardRow(
    row: ExceptionRow,
    isTopRow: Boolean,
    isBottomRow: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NiraCardRow(
        title = row.origin,
        isTopRow = isTopRow,
        isBottomRow = isBottomRow,
        showDivider = !isBottomRow,
        onClick = onClick,
        modifier = modifier,
        leading = {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        trailing = {
            Text(
                text = row.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.site_exceptions_remove_row),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/**
 * Count of permissions on this row that have deviated from their
 * `NO_DECISION` default (or, for autoplay, from `BLOCKED` which is the
 * Gecko default). Autoplay fields are tri-counted only if the user
 * explicitly flipped them to ALLOWED - BLOCKED is the ambient default
 * for every unvisited site.
 */
private fun SitePermissions.nonDefaultCount(): Int {
    var n = 0
    if (location != Status.NO_DECISION) n++
    if (notification != Status.NO_DECISION) n++
    if (microphone != Status.NO_DECISION) n++
    if (camera != Status.NO_DECISION) n++
    if (bluetooth != Status.NO_DECISION) n++
    if (localStorage != Status.NO_DECISION) n++
    if (mediaKeySystemAccess != Status.NO_DECISION) n++
    if (crossOriginStorageAccess != Status.NO_DECISION) n++
    if (localDeviceAccess != Status.NO_DECISION) n++
    if (localNetworkAccess != Status.NO_DECISION) n++
    if (autoplayAudible == AutoplayStatus.ALLOWED) n++
    if (autoplayInaudible == AutoplayStatus.ALLOWED) n++
    return n
}
