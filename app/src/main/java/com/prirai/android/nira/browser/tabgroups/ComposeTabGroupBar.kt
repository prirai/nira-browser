package com.prirai.android.nira.browser.tabgroups

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.prirai.android.nira.browser.tabs.compose.TabOrderManager
import com.prirai.android.nira.browser.tabs.compose.UnifiedTabOrder
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ui.theme.NiraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.lib.state.ext.flowScoped

/**
 * Compose-based horizontal tab group bar that appears above the address bar.
 * Shows current tab groups and allows switching between them.
 * Replaces the RecyclerView-based TabGroupBar.
 */
class ComposeTabGroupBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
    }

    private var listener: TabGroupBarListener? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var tabGroupManager: TabGroupManager? = null
    private var tabOrderManager: TabOrderManager? = null

    init {
        orientation = HORIZONTAL
        addView(composeView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ))
    }

    fun setup(
        tabGroupManager: TabGroupManager,
        lifecycleOwner: LifecycleOwner,
        listener: TabGroupBarListener
    ) {
        this.tabGroupManager = tabGroupManager
        this.listener = listener
        this.lifecycleOwner = lifecycleOwner

        // Initialize tab order manager
        val unifiedManager = UnifiedTabGroupManager.getInstance(context)
        tabOrderManager = TabOrderManager.getInstance(context, unifiedManager)

        setupComposeContent()
        setupStoreObserver()
    }

    private fun setupComposeContent() {
        composeView.setContent {
            NiraTheme {
                TabGroupBarContent()
            }
        }
    }

    @Composable
    private fun TabGroupBarContent() {
        val orderManager = tabOrderManager ?: return
        val currentOrder by orderManager.currentOrder.collectAsState()

        currentOrder?.let { order ->
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = order.primaryOrder,
                    key = { item ->
                        when (item) {
                            is UnifiedTabOrder.OrderItem.SingleTab -> item.tabId
                            is UnifiedTabOrder.OrderItem.TabGroup -> item.groupId
                        }
                    }
                ) { item ->
                    when (item) {
                        is UnifiedTabOrder.OrderItem.SingleTab -> {
                            SingleTabItem(item.tabId)
                        }
                        is UnifiedTabOrder.OrderItem.TabGroup -> {
                            TabGroupItem(item)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SingleTabItem(tabId: String) {
        val store = context.components.store
        val tab by produceState<TabSessionState?>(initialValue = null) {
            store.flowScoped(lifecycleOwner!!) { flow ->
                flow.map { it.tabs.find { tab -> tab.id == tabId } }
                    .distinctUntilChanged()
                    .collect { value = it }
            }
        }

        tab?.let { tabState ->
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .height(48.dp)
                    .clickable { listener?.onTabSelected(tabId) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = tabState.content.title.takeIf { it.isNotBlank() } ?: tabState.content.url,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            context.components.tabsUseCases.removeTab(tabId)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Close tab",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TabGroupItem(groupItem: UnifiedTabOrder.OrderItem.TabGroup) {
        Card(
            modifier = Modifier
                .width(140.dp)
                .height(48.dp)
                .clickable {
                    lifecycleOwner?.lifecycleScope?.launch {
                        tabOrderManager?.toggleGroupExpansion(groupItem.groupId)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(groupItem.color).copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(groupItem.color))
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupItem.groupName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${groupItem.tabIds.size} tabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        lifecycleOwner?.lifecycleScope?.launch {
                            tabOrderManager?.addNewTabToGroup(groupItem.groupId)
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Add,
                        contentDescription = "Add tab to group",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    private fun setupStoreObserver() {
        val lifecycleOwner = lifecycleOwner ?: return
        val store = context.components.store

        lifecycleOwner.lifecycleScope.launch {
            store.flowScoped(lifecycleOwner) { flow ->
                flow.collect { state ->
                    // Trigger recomposition when tabs change
                    setupComposeContent()
                }
            }
        }
    }

    /**
     * Force refresh the current group display with updated selection state.
     */
    fun refreshSelection() {
        setupComposeContent()
    }

    interface TabGroupBarListener {
        fun onTabSelected(tabId: String)
    }
}