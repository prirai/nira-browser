package com.prirai.android.nira.browser.profile

import android.content.Context
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.recover.RecoverableTab
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import mozilla.components.support.base.log.logger.Logger

/**
 * One-time migration: reassigns every tab whose [mozilla.components.browser.state.state.TabSessionState.contextId]
 * is `null` or empty to the built-in default profile's contextId (`"profile_default"`).
 *
 * # Why this exists
 * Nira used to permit tabs to be created without a `contextId`, in which case the
 * various filter callsites (`UnifiedToolbar.kt`, `ComposeHomeFragment.kt`, `TabViewModel.kt`)
 * treated them as belonging to whichever profile was displayed at the time. The result
 * was that after a restart these tabs stopped appearing in the tab bar / tab sheet
 * even though Gecko continued to render them. This is a one-time correction: any
 * such orphan tab is adopted by the built-in default profile, permanently.
 *
 * # How it runs
 * Instead of a hand-rolled remove + re-add pass (which loses `engineState`,
 * thumbnails, scroll position and `parentId`), this hook intercepts
 * [TabListAction.RestoreAction] as it flows through the store's middleware chain
 * and rewrites `contextId` on each [RecoverableTab] *before* the reducer sees it.
 * `engineSessionState` and every other field are preserved verbatim.
 *
 * # Gating
 * Runs only when [UserPreferences.defaultProfileMigrationDone] is `false`. Once
 * a `RestoreAction` with matching tabs has been rewritten, the flag flips and:
 *   - [UserPreferences.pendingDefaultMigrationPopup] is set so the next
 *     `BrowserActivity` shows the "migration complete" dialog exactly once.
 *   - [ProfileManager.setActiveProfile] is called with the built-in default so
 *     the user lands on the profile that now contains the tabs.
 *   - Any `TabGroup` row in the Room DB with `contextId in (NULL, "")` is
 *     bulk-updated to `"profile_default"` on `Dispatchers.IO`.
 *
 * Private-mode tabs and tabs already carrying a valid `contextId` are left alone.
 *
 * Backed by the preference key
 * [UserPreferences.Companion.NIRA_TABS_PROFILE_DEFAULT_MIGRATE] (`"nira.tabs.profile.defaultMigrate"`).
 */
class DefaultProfileTabMigration(
    private val applicationContext: Context,
) : Middleware<BrowserState, BrowserAction> {

    private val logger = Logger("DefaultProfileTabMigration")
    private val prefs by lazy { UserPreferences(applicationContext) }

    // Long-lived scope for the follow-up Room / prefs work. Deliberately not tied to
    // any Activity lifecycle - the migration must complete even if the user backgrounds
    // the app immediately after cold-start.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        // Fast path: once the migration flag is set we are a no-op for the entire process.
        if (prefs.defaultProfileMigrationDone) {
            next(action)
            return
        }

        if (action !is TabListAction.RestoreAction) {
            next(action)
            return
        }

        // Identify the tabs that need adopting. Skip private tabs and anything that
        // already has a real contextId (profile_*, private, or any container id).
        val (needsRewrite, keepAsIs) = action.tabs.partition { rt ->
            val ctx = rt.state.contextId
            !rt.state.private && (ctx == null || ctx.isEmpty())
        }

        if (needsRewrite.isEmpty()) {
            // Nothing to migrate in this restore batch, but the user has run the app at
            // least once - flip the flag so we don't scan future restores forever.
            markMigrationDoneEmpty()
            next(action)
            return
        }

        logger.info(
            "Rewriting contextId on ${needsRewrite.size} restored tab(s) to " +
                "\"$DEFAULT_CONTEXT_ID\" (leaving ${keepAsIs.size} tab(s) untouched)",
        )

        val rewrittenTabs: List<RecoverableTab> = needsRewrite.map { rt ->
            rt.copy(state = rt.state.copy(contextId = DEFAULT_CONTEXT_ID))
        }

        // Preserve original ordering by rebuilding from the two partitions in the same
        // relative order they arrived in. `partition` preserves per-list order but not
        // the interleaving between the two lists, so we do a second pass keyed on tab id.
        val rewrittenById = rewrittenTabs.associateBy { it.state.id }
        val merged: List<RecoverableTab> = action.tabs.map { original ->
            rewrittenById[original.state.id] ?: original
        }

        val rewrittenAction = action.copy(tabs = merged)
        next(rewrittenAction)

        // Follow-up: persist the migration marker, activate default profile, and rewrite
        // any TabGroup rows that were pinned to a null/empty contextId.
        finaliseMigration(migratedTabCount = needsRewrite.size)
    }

    private fun markMigrationDoneEmpty() {
        // Even if the restore batch was empty (fresh install, or the user had zero
        // orphan tabs), commit the flag so we stop scanning. No popup is shown in
        // this case - there was nothing to tell the user about.
        prefs.defaultProfileMigrationDone = true
    }

    private fun finaliseMigration(migratedTabCount: Int) {
        // Commit these synchronously so a subsequent RestoreAction on the same process
        // (unusual, but possible via `TabsUseCases.restore` fan-out) short-circuits.
        prefs.defaultProfileMigrationDone = true
        prefs.pendingDefaultMigrationPopup = true
        prefs.pendingDefaultMigrationTabCount = migratedTabCount

        scope.launch {
            try {
                // Force-activate the built-in default profile so the tabs we just adopted
                // are the ones shown by the tab bar filter. ProfileManager caches its
                // active-id in SharedPreferences and fires ProfileAddonPolicy + search
                // engine updates on its own; safe to call from any thread.
                val pm = ProfileManager.getInstance(applicationContext)
                val defaultProfile = pm.getAllProfiles().firstOrNull { it.isDefault }
                    ?: BrowserProfile.getDefaultProfile()
                pm.setActiveProfile(defaultProfile)
            } catch (t: Throwable) {
                logger.error("Failed to activate default profile during migration", t)
            }

            try {
                // Move tab groups that were tagged with a null/empty contextId to the
                // default profile so they follow their (now-migrated) tabs. This uses a
                // raw SQL UPDATE via TabGroupDao.reassignEmptyContextIdGroups; the
                // in-memory UnifiedTabGroupManager cache will refresh next time it
                // reloads (either via loadGroupsFromDatabase() on process start or
                // via a group edit that triggers emitStateUpdate()).
                val db = com.prirai.android.nira.browser.tabgroups.TabGroupDatabase
                    .getInstance(applicationContext)
                val updated = db.tabGroupDao().reassignEmptyContextIdGroups(DEFAULT_CONTEXT_ID)
                if (updated > 0) {
                    logger.info("Reassigned $updated tab group row(s) to \"$DEFAULT_CONTEXT_ID\"")
                }
            } catch (t: Throwable) {
                logger.error("Failed to reassign tab group contextIds during migration", t)
            }
        }
    }

    companion object {
        /**
         * The canonical contextId string used by the built-in default profile.
         * Matches the interpolation `"profile_${BrowserProfile.getDefaultProfile().id}"`
         * throughout the codebase.
         */
        const val DEFAULT_CONTEXT_ID: String = "profile_default"
    }
}
