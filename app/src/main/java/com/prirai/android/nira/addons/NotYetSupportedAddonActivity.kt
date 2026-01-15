package com.prirai.android.nira.addons

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.prirai.android.nira.R
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.UnsupportedAddonsAdapter
import mozilla.components.feature.addons.ui.UnsupportedAddonsAdapterDelegate
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.ext.getParcelableArrayListExtraCompat
import com.prirai.android.nira.ext.getParcelableArrayListCompat
import com.prirai.android.nira.theme.applyCompleteTheme
import com.prirai.android.nira.ext.enableEdgeToEdgeMode
import com.prirai.android.nira.ext.applyPersistentInsets

// Activity for managing unsupported add-ons, or add-ons that were installed but are no longer available.

class NotYetSupportedAddonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_base)
        
        applyCompleteTheme(this)
        
        // Enable edge-to-edge with standardized approach
        enableEdgeToEdgeMode()

        val addons = requireNotNull(intent.getParcelableArrayListExtraCompat<Addon>("add_ons"))

        // Apply insets to container
        findViewById<View>(R.id.container)?.applyPersistentInsets()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.container, NotYetSupportedAddonFragment.create(addons))
            .commit()
    }

    override fun onResume() {
        super.onResume()
        com.prirai.android.nira.theme.ThemeManager.applySystemBarsTheme(this, false)
    }

    // Fragment for managing add-ons that are not yet supported by the browser.
    class NotYetSupportedAddonFragment : Fragment(), UnsupportedAddonsAdapterDelegate {
        private lateinit var addons: List<Addon>
        private var adapter: UnsupportedAddonsAdapter? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            addons = requireNotNull(arguments?.getParcelableArrayListCompat("add_ons"))
            return inflater.inflate(R.layout.fragment_other_addons, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val context = requireContext()
            val recyclerView: RecyclerView = view.findViewById(R.id.unsupported_add_ons_list)
            adapter = UnsupportedAddonsAdapter(
                addonManager = context.components.addonManager,
                unsupportedAddonsAdapterDelegate = this@NotYetSupportedAddonFragment,
                addons = addons
            )

            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = adapter
        }

        override fun onUninstallError(addonId: String, throwable: Throwable) {
            Toast.makeText(context, "Failed to remove add-on", Toast.LENGTH_SHORT).show()
        }

        override fun onUninstallSuccess() {
            Toast.makeText(context, "Successfully removed add-on", Toast.LENGTH_SHORT)
                .show()
            if (adapter?.itemCount == 0) {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }
        }

        companion object {
            fun create(addons: ArrayList<Addon>) = NotYetSupportedAddonFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList("add_ons", addons)
                }
            }
        }
    }
}
