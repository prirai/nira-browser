package com.prirai.android.nira.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.prirai.android.nira.R
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.privacy.cookies.CookiePolicy
import com.prirai.android.nira.privacy.cookies.CookiePolicyRepository
import com.prirai.android.nira.ssl.SitePermissionsBottomSheet
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
 * two sources). A row shows:
 *   - the origin
 *   - a one-line summary describing which permissions deviate from
 *     defaults and whether a cookie-retention policy exists
 *   - a trailing "remove" button that drops this origin's AC row and
 *     cookie policy (without touching other origins)
 */
class SiteExceptionsFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: ExceptionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_site_exceptions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { parentFragmentManager.popBackStack() }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_clear_all -> {
                        showClearAllConfirmation()
                        true
                    }
                    else -> false
                }
            }
        }

        recycler = view.findViewById(R.id.exceptions_list)
        emptyView = view.findViewById(R.id.empty_view)
        adapter = ExceptionsAdapter(
            onClick = { origin -> openSheetFor(origin) },
            onRemove = { origin -> removeExceptionsFor(origin) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        reload()
    }

    override fun onResume() {
        super.onResume()
        // Pick up any changes made via the bottom sheet while this
        // screen was still in the back stack.
        reload()
    }

    private fun reload() {
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val perms = runCatching {
                    context.components.permissionStorage.all()
                }.getOrElse { emptyList() }
                val policies = CookiePolicyRepository.getInstance(context).getAll()

                buildRows(perms, policies)
            }
            if (!isAdded) return@launch
            adapter.submitList(rows)
            emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            recycler.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
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

    private fun showClearAllConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.site_exceptions_clear_all)
            .setMessage(R.string.site_exceptions_clear_all_confirm)
            .setPositiveButton(R.string.site_exceptions_clear_all) { _, _ -> clearAll() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    private data class ExceptionRow(val origin: String, val summary: String)

    private class ExceptionsAdapter(
        private val onClick: (String) -> Unit,
        private val onRemove: (String) -> Unit,
    ) : RecyclerView.Adapter<ExceptionsAdapter.VH>() {

        private val rows = mutableListOf<ExceptionRow>()

        fun submitList(newRows: List<ExceptionRow>) {
            rows.clear()
            rows.addAll(newRows)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_site_exception, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            holder.origin.text = row.origin
            holder.summary.text = row.summary
            holder.itemView.setOnClickListener { onClick(row.origin) }
            holder.remove.setOnClickListener { onRemove(row.origin) }
        }

        override fun getItemCount(): Int = rows.size

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val origin: TextView = itemView.findViewById(R.id.row_origin)
            val summary: TextView = itemView.findViewById(R.id.row_summary)
            val remove: ImageButton = itemView.findViewById(R.id.row_remove)
        }
    }
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
