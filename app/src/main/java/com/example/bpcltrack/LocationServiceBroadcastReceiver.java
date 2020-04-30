package com.example.bpcltrack;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.location.LocationResult;
import com.google.firebase.firestore.FirebaseFirestore;

public class LocationServiceBroadcastReceiver extends BroadcastReceiver {

    // TODO: send updated notifs to map activity using weak activity ref and control progress bar

    public static final String ACTION_PROCESS_UPDATE = "com.example.bpcltrack.UPDATE_LOCATION";
    private static final String TAG = "TAG";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (ACTION_PROCESS_UPDATE.equals(intent.getAction())) {
                LocationResult locationResult = LocationResult.extractResult(intent);
                if (locationResult != null) {
                    Location location = locationResult.getLastLocation();

                    Log.d(TAG, "onReceive: " + location.getLatitude() + " " + location.getLongitude());
                }
            }
        }
    }
}
