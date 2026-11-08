package com.prirai.android.nira.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.prirai.android.nira.R
import com.prirai.android.nira.settings.fragment.showNiraInfoDialog

/**
 * A preference whose explanatory summary lives behind an info icon instead
 * of the row itself: the list stays clean (title only), and the 'i' glyph
 * in the widget area opens a [com.prirai.android.nira.ui.components.NiraAlertDialog]
 * with the explanation (`app:infoText`). Tapping the icon never triggers
 * the row's own click behavior.
 */
open class InfoPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private var infoTextRes: Int = 0

    init {
        context.obtainStyledAttributes(
            attrs, R.styleable.InfoPreference, defStyleAttr, defStyleRes
        ).apply {
            infoTextRes = getResourceId(R.styleable.InfoPreference_infoText, 0)
            recycle()
        }
        widgetLayoutResource = R.layout.preference_widget_info
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.pref_info_icon)?.setOnClickListener {
            showNiraInfoDialog(context, title?.toString().orEmpty(), infoTextRes)
        }
    }
}

/**
 * [MaterialSwitchPreference] with the same info-icon affordance: the
 * switch keeps its `@android:id/switch_widget` binding, the trailing 'i'
 * glyph opens the explanation dialog.
 */
class InfoSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.switchPreferenceCompatStyle,
    defStyleRes: Int = 0
) : MaterialSwitchPreference(context, attrs, defStyleAttr, defStyleRes) {

    private var infoTextRes: Int = 0

    init {
        context.obtainStyledAttributes(
            attrs, R.styleable.InfoPreference, defStyleAttr, defStyleRes
        ).apply {
            infoTextRes = getResourceId(R.styleable.InfoPreference_infoText, 0)
            recycle()
        }
        widgetLayoutResource = R.layout.preference_widget_material_switch_info
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.pref_info_icon)?.setOnClickListener {
            showNiraInfoDialog(context, title?.toString().orEmpty(), infoTextRes)
        }
    }
}
