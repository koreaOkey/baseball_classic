package com.basehaptic.mobile.wear

sealed class WatchCompanionStatus {
    object Loading : WatchCompanionStatus()
    object PairedNone : WatchCompanionStatus()
    object PairedNoApp : WatchCompanionStatus()
    object Installed : WatchCompanionStatus()
}
