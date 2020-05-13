package com.example.bpcltrack;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

public class MeasurementActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private static final long FASTEST_UPDATE_INTERVAL = 5000L;
    private static final long UPDATE_INTERVAL = 10000L;

    private Location location;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("location")) {
            location = (Location) getIntent().getExtras().get("location");

            gotLocation();
        } else {
            Dexter.withContext(this)
                    .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MeasurementActivity.this);

                            locationRequest = new LocationRequest();
                            locationRequest.setInterval(UPDATE_INTERVAL);
                            locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
                            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                            fusedLocationProviderClient.requestLocationUpdates(
                                    locationRequest,
                                    locationCallback,
                                    MeasurementActivity.this.getMainLooper()
                            );
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                            permissionToken.continuePermissionRequest();
                        }
                    })
                    .check();
        }
    }

    private void gotLocation() {

    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            // todo: update ui

            location = locationResult.getLastLocation();
            gotLocation();
        }
    };
}
