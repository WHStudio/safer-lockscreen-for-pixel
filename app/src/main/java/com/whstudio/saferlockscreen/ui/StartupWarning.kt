package com.whstudio.saferlockscreen.ui

/** The warning is shown until the user confirms it once. Clearing app data shows it again. */
internal fun shouldShowStartupWarning(acknowledged: Boolean): Boolean = !acknowledged
