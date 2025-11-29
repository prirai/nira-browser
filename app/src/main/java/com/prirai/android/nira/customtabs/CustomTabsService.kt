package com.prirai.android.nira.customtabs

import mozilla.components.concept.engine.Engine
import mozilla.components.feature.customtabs.AbstractCustomTabsService
import com.prirai.android.nira.ext.components

class CustomTabsService : AbstractCustomTabsService() {
    override val engine: Engine by lazy { components.engine }
    override val customTabsServiceStore by lazy { components.customTabsStore }
}
