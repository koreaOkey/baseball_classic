package com.basehaptic.mobile.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object WatchCompanionStatusRepository {
    private const val TAG = "WatchCompanionStatus"
    private const val WATCH_CAPABILITY = "basehaptic_watch_app"

    suspend fun getStatus(context: Context): WatchCompanionStatus {
        return try {
            val nodeClient = Wearable.getNodeClient(context)
            val connectedNodes = nodeClient.connectedNodes.await()
            Log.d(
                TAG,
                "connected=${connectedNodes.size} ${connectedNodes.map { "${it.id}:${it.displayName}" }}"
            )
            if (connectedNodes.isEmpty()) {
                return WatchCompanionStatus.PairedNone
            }

            val localNodeId = nodeClient.localNode.await().id
            val capabilityClient = Wearable.getCapabilityClient(context)
            // Reason: FILTER_REACHABLE 은 GMS 의 stale 캐시를 우회하고 실제로 통신 가능한 노드만 본다.
            val capabilityInfo = capabilityClient
                .getCapability(WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            // Reason: 워치 앱과 폰 앱이 동일 applicationId 이므로 capability 결과에 LocalNode 가
            // 포함될 수 있다. 워치 노드만 카운트하기 위해 LocalNode 제거.
            val watchNodes = capabilityInfo.nodes.filter { it.id != localNodeId }
            Log.d(
                TAG,
                "capability='$WATCH_CAPABILITY' total=${capabilityInfo.nodes.size} watch=${watchNodes.size} " +
                    capabilityInfo.nodes.map { "${it.id}:${it.displayName}(near=${it.isNearby})" }
            )
            if (watchNodes.isEmpty()) {
                WatchCompanionStatus.PairedNoApp
            } else {
                WatchCompanionStatus.Installed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve watch companion status", e)
            WatchCompanionStatus.PairedNone
        }
    }
}
