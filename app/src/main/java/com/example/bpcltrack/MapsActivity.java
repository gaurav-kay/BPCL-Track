package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.AbstractSequentialList;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity {

    private static final String TAG = "TAG";

    private static final long FASTEST_UPDATE_INTERVAL = 5000L;
    private static final long UPDATE_INTERVAL = 10000L;
    private static final float DEVIATION_THRESHOLD = 500f;
    private static final long REPORT_INTERVAL = 5 * 60 * 1000;

    private boolean isTripStarted = false;
    private boolean isReportRecentlyMade = false;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    protected GoogleMap mMap;
    private PolylineOptions tripPolyLineOptions;
    private LatLngBounds.Builder cameraBounds;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    protected ProgressBar progressBar;
    private Button startStopTrip, alertButton;
    private SupportMapFragment mapFragment;

    private ArrayList<Location> locations;
    protected ArrayList<LatLng> pipelineLatLngs = new ArrayList<>();
    private HashMap<String, Object> tripDetails;
    private ArrayList<Polyline> tripPolylines;
    private Marker startLocationMarker, endLocationMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mAuth = FirebaseAuth.getInstance();

        progressBar = findViewById(R.id.progress_bar);
        startStopTrip = findViewById(R.id.start_stop_trip);
        alertButton = findViewById(R.id.alert_button);
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        Dexter.withContext(this)
                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                        gotLocationPermissions();

                        mapFragment.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(GoogleMap googleMap) {
                                mMap = googleMap;

                                mMap.setMyLocationEnabled(true);
                                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                                tripPolyLineOptions = new PolylineOptions().clickable(true);
                                tripPolylines = new ArrayList<>();
                                cameraBounds = new LatLngBounds.Builder();
                                loadPipelines();
                            }
                        });
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                })
                .check();

        startStopTrip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTripStarted) {
                    Toast.makeText(MapsActivity.this, "Trip started", Toast.LENGTH_SHORT).show();

                    locations = new ArrayList<>();
                    tripDetails = new HashMap<>();
                    tripDetails.put("startTime", new Date().getTime());

                    fusedLocationProviderClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            MapsActivity.this.getMainLooper()
                    );

                    startStopTrip.setText(R.string.stop_button_text);
                    alertButton.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(MapsActivity.this, "Trip Completed, Uploading...", Toast.LENGTH_SHORT).show();

                    fusedLocationProviderClient.removeLocationUpdates(locationCallback);

                    tripDetails.put("endTime", new Date().getTime());

                    startStopTrip.setText(R.string.start_button_text);
                    alertButton.setVisibility(View.INVISIBLE);

                    // end marker
                    endLocationMarker = mMap.addMarker(new MarkerOptions().position(latLngFromLocation(locations.get(locations.size() - 1))).title("End Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 150));

                    uploadTrip();
                }
                isTripStarted = !isTripStarted;
            }
        });

        alertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, ReportActivity.class);
                intent.putExtra("locations", locations);
//                Bundle bundle = new Bundle();
//                bundle.putSerializable("locations", locations);
//                intent.
                startActivity(intent);
            }
        });
    }

    private void loadPipelines() {
        LoadKml loadKml = new LoadKml(this);
        loadKml.execute();
    }

    private void uploadTrip() {
        tripDetails.put("locations", locations);

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date()))

                .set(tripDetails)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        resetMap();

                        Toast.makeText(MapsActivity.this, "Uploaded", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d(TAG, "onFailure: " + e);
                    }
                });
    }

    private void resetMap() {
        for (Polyline polyline : tripPolylines) {
            polyline.remove();
        }
        startLocationMarker.remove();
        endLocationMarker.remove();
    }

    private void gotLocationPermissions() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        progressBar.setVisibility(View.INVISIBLE);
    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            if (isTripDeviated(locationResult.getLastLocation()) && !isReportRecentlyMade) {
                // todo: notification
                Toast.makeText(MapsActivity.this, "Going off course", Toast.LENGTH_SHORT).show();

                makeDeviationReport(locationResult.getLastLocation());
            }

            locations.add(locationResult.getLastLocation());
            updateMap(locationResult.getLastLocation());
        }
    };

    private void makeDeviationReport(Location lastLocation) {
        HashMap<String, Object> map = new HashMap<>();

        map.put("locations", locations);
        map.put("lastLocation", lastLocation);
        map.put("reportTime", new Date().getTime());
        map.put("uid", mAuth.getCurrentUser().getUid());

        db.collection("reports")
                .document(mAuth.getCurrentUser().getUid() + " " + simpleDateFormat.format(new Date()))

                .set(map)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        isReportRecentlyMade = true;

                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isReportRecentlyMade = false;
                            }
                        }, REPORT_INTERVAL);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }

    private boolean isTripDeviated(Location lastLocation) {
        boolean deviated = true;
        for (LatLng pipelineLatLng : pipelineLatLngs) {
            float[] results = new float[1];
            Location.distanceBetween(pipelineLatLng.latitude, pipelineLatLng.longitude, lastLocation.getLatitude(), lastLocation.getLongitude(), results);

            if (results[0] < DEVIATION_THRESHOLD) {
                deviated = false;
                break;
            }
        }
        return deviated;
    }

    private void updateMap(Location location) {
        if (locations.size() == 1) {
            // start marker
            startLocationMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title("Start Location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16F));
        }
        tripPolylines.add(mMap.addPolyline(tripPolyLineOptions.add(
                latLngFromLocation(locations.get(locations.size() - 1)),
                latLngFromLocation(location)
        )));
        cameraBounds.include(latLngFromLocation(location));
    }

    private LatLng latLngFromLocation(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }
}

// todo: change distance for location req
