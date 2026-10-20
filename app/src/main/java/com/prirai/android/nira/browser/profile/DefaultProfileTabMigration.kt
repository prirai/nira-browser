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
 * One-time migration: reassigns every tab whose
 * [mozilla.components.browser.state.state.TabSessionState.contextId]
 * is `"profile_default"` (the legacy sentinel for the built-in default
 * profile) back to `null`.
 *
 * # Why this exists
 * Previously Nira stamped new tabs created under the default profile with
 * `contextId = "profile_default"`, matching the string pattern used for
 * every other named profile (`profile_<id>`). That worked for the main
 * browser flow, but GeckoView treats custom tabs (external VIEW intents)
 * with `contextId = null`, i.e. the default partition. As a result, a user
 * who signed into LinkedIn in Nira (contextId `"profile_default"`) did not
 * carry the session into links opened from external apps (which land in
 * a custom tab with `contextId = null`) - the two were different Gecko
 * partitions.
 *
 * Unifying on `null` for the default profile removes that split and makes
 * the default partition the single source of truth for cookies, storage
 * and permissions.
 *
 * # How it runs
 * This hook intercepts [TabListAction.RestoreAction] as it flows through
 * the store's middleware chain and rewrites `contextId` on each
 * [RecoverableTab] *before* the reducer sees it. `engineSessionState`,
 * thumbnails, scroll position, `parentId` and every other field are
 * preserved verbatim.
 *
 * # Gating
 * Runs only when [UserPreferences.defaultProfileMigrationDone] is `false`.
 * Because we previously shipped a migration that moved `null -> "profile_default"`,
 * the pref key has been bumped to [UserPreferences.Companion.NIRA_TABS_PROFILE_DEFAULT_MIGRATE]
 * (`"nira.tabs.profile.defaultMigrate.v2"`) so installs upgraded past that
 * earlier migration get this one applied exactly once more.
 *
 * On completion:
 *   - Any `TabGroup` row in the Room DB with `contextId == "profile_default"`
 *     is bulk-updated to `null` on `Dispatchers.IO`.
 *
 * No user-facing popup is shown for v2: the end user never saw a distinction
 * between the two sentinels, and the plumbing change is invisible from the
 * UI (tabs continue to appear under the Default profile). The transient
 * popup prefs are left untouched by this migration.
 *
 * Private-mode tabs and tabs carrying any other contextId (`"profile_<id>"`
 * for a user-defined profile, `"private"`, or null) are left alone.
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

        // Identify the tabs that need unmarking. Skip private tabs and anything
        // whose contextId is not the legacy default sentinel.
        val (needsRewrite, keepAsIs) = action.tabs.partition { rt ->
            !rt.state.private && rt.state.contextId == LEGACY_DEFAULT_CONTEXT_ID
        }

        if (needsRewrite.isEmpty()) {
            // Nothing to migrate in this restore batch, but the user has run the app at
            // least once - flip the flag so we don't scan future restores forever.
            markMigrationDoneEmpty()
            next(action)
            return
        }

        logger.info(
            "Clearing legacy \"$LEGACY_DEFAULT_CONTEXT_ID\" contextId on " +
                "${needsRewrite.size} restored tab(s) (leaving ${keepAsIs.size} untouched)",
        )

        val rewrittenTabs: List<RecoverableTab> = needsRewrite.map { rt ->
            rt.copy(state = rt.state.copy(contextId = null))
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

        // Follow-up: persist the migration marker and rewrite any TabGroup rows
        // that were pinned to the legacy default sentinel.
        finaliseMigration(migratedTabCount = needsRewrite.size)
    }

    private fun markMigrationDoneEmpty() {
        // Even if the restore batch was empty (fresh install, or the user had zero
        // orphan tabs), commit the flag so we stop scanning. No popup is shown in
        // this case - there was nothing to tell the user about.
        prefs.defaultProfileMigrationDone = true
    }

    private fun finaliseMigration(migratedTabCount: Int) {
        // Commit synchronously so a subsequent RestoreAction on the same
        // process (unusual, but possible via `TabsUseCases.restore` fan-out)
        // short-circuits. No popup is scheduled - this migration is a pure
        // plumbing change (sentinel -> null) with no user-visible effect.
        prefs.defaultProfileMigrationDone = true
        logger.info("Default-profile v2 migration committed ($migratedTabCount tab(s) cleared)")

        scope.launch {
            try {
                // Move tab groups that were tagged with the legacy default
                // sentinel to `null` so they follow their (now-migrated) tabs.
                // The in-memory UnifiedTabGroupManager cache will refresh next
                // time it reloads.
                val db = com.prirai.android.nira.browser.tabgroups.TabGroupDatabase
                    .getInstance(applicationContext)
                val updated = db.tabGroupDao()
                    .reassignContextIdGroups(from = LEGACY_DEFAULT_CONTEXT_ID, to = null)
                if (updated > 0) {
                    logger.info("Cleared $updated tab group row(s) from \"$LEGACY_DEFAULT_CONTEXT_ID\" to null")
                }
            } catch (t: Throwable) {
                logger.error("Failed to clear tab group contextIds during migration", t)
            }
        }
    }

    companion object {
        /**
         * The legacy sentinel string that previous versions of Nira wrote
         * for tabs created under the built-in default profile.
         *
         * Starting with this release the default profile shares Gecko's
         * default partition (contextId == null) with custom tabs. New
         * code should never write this value; existing persisted rows are
         * migrated away by [DefaultProfileTabMigration].
         */
        const val LEGACY_DEFAULT_CONTEXT_ID: String = "profile_default"
    }
}
