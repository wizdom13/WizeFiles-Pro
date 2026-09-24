package com.wisso.wizefiles.feature.appmanager

internal enum class AppEnabledAction {
    ENABLE,
    DISABLE
}

internal fun canUninstallSelectedApps(selectedApps: List<InstalledApp>): Boolean =
    selectedApps.isNotEmpty() && selectedApps.none(InstalledApp::isSystem)

internal fun selectedAppEnabledAction(
    selectedApps: List<InstalledApp>
): AppEnabledAction? {
    val app = selectedApps.singleOrNull() ?: return null
    if (!app.isSystem && app.isEnabled) {
        return null
    }
    return if (app.isEnabled) AppEnabledAction.DISABLE else AppEnabledAction.ENABLE
}
