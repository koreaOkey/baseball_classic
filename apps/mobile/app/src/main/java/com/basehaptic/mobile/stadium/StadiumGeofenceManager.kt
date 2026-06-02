package com.basehaptic.mobile.stadium

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class StadiumGeofenceManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(appContext)

    fun buildGeofences(stadiums: List<StadiumInfo>): List<Geofence> {
        return stadiums.map { stadium ->
            Geofence.Builder()
                .setRequestId(stadium.code)
                .setCircularRegion(stadium.latitude, stadium.longitude, stadium.radiusMeters)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
    }

    fun buildRequest(stadiums: List<StadiumInfo>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(buildGeofences(stadiums))
            .build()
    }

    fun startMonitoringDark(stadiums: List<StadiumInfo>, pendingIntent: PendingIntent): Boolean {
        buildRequest(stadiums)
        geofencingClient
        pendingIntent
        return false
    }
}
