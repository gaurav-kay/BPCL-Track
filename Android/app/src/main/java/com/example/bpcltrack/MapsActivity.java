package com.example.bpcltrack;

import android.Manifest;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.getbase.floatingactionbutton.FloatingActionButton;
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
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.text.SimpleDateFormat;
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
    private static final long DEVIATION_REPORT_INTERVAL = 1 * 60 * 1000;
    private static final int CHAINAGE_INTERVAL = 10;
    private static final int CHAINAGE_START = 0;
    private static final int CHAINAGE_END = 600;

    private boolean isTripStarted = false;
    private boolean isLocationReceived = false;
    private boolean isDeviationReportRecentlyMade = false;

    protected ProgressBar progressBar;
    private FloatingActionButton startStopTripFab, alertFab, takeMeasurementFab, signOutFab;
    private SupportMapFragment mapFragment;
    private Spinner chainageSpinner;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    protected GoogleMap mMap;
    private LatLngBounds.Builder cameraBounds;
    private Marker startLocationMarker, endLocationMarker;
    private PolylineOptions tripPolylineOptions;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private FirebaseAuth mAuth;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    protected ArrayList<LatLng> pipelineLatLngs;
    private ArrayList<Location> locations;
    private ArrayList<Polyline> tripPolylines;
    private HashMap<String, Object> tripDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        mAuth = FirebaseAuth.getInstance();

        progressBar = findViewById(R.id.progress_bar);
        chainageSpinner = findViewById(R.id.chainage_spinner);
        startStopTripFab = findViewById(R.id.start_stop_trip_fab);
        takeMeasurementFab = findViewById(R.id.take_measurement_fab);
        signOutFab = findViewById(R.id.log_out_fab);
        alertFab = findViewById(R.id.alert_fab);
        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.map);

        // ui
        startStopTripFab.setVisibility(View.GONE);
        alertFab.setVisibility(View.GONE);
        chainageSpinner.setVisibility(View.GONE);

        Dexter.withContext(this)
                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                        mapFragment.getMapAsync(new OnMapReadyCallback() {
                            @Override
                            public void onMapReady(GoogleMap googleMap) {
                                mMap = googleMap;

                                mMap.setMyLocationEnabled(true);
                                mMap.getUiSettings().setMyLocationButtonEnabled(true);

                                // load pipelines
                                LoadKml loadKml = new LoadKml(MapsActivity.this, true, false, false);
                                loadKml.execute();
                            }
                        });

                        // build location request, got permissions
                        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);

                        locationRequest = new LocationRequest();
                        locationRequest.setInterval(UPDATE_INTERVAL);
                        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
                        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                        // make buttons visible ui
                        startStopTripFab.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();

        startStopTripFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTripStarted) {
                    Toast.makeText(MapsActivity.this, "Trip Started", Toast.LENGTH_SHORT).show();

                    // reset vars or in resetMap
                    locations = new ArrayList<>();
                    tripPolylines = new ArrayList<>();
                    tripPolylineOptions = new PolylineOptions();
                    cameraBounds = new LatLngBounds.Builder();
                    tripDetails = new HashMap<>();
                    isLocationReceived = false;  // sort of like first run, so set to false
                    tripDetails.put("startTime", new Date().getTime());
                    tripDetails.put("isOngoing", true);
                    tripDetails.put("by", mAuth.getCurrentUser().getEmail());

                    // ui
                    alertFab.setVisibility(View.VISIBLE);
                    alertFab.setEnabled(false);
                    alertFab.setTitle(getResources().getString(R.string.getting_location));
                    chainageSpinner.setVisibility(View.VISIBLE);

                    fusedLocationProviderClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            MapsActivity.this.getMainLooper()
                    );
                } else {
                    tripDetails.put("endTime", new Date().getTime());

                    Toast.makeText(MapsActivity.this, "Trip Completed, Uploading...", Toast.LENGTH_SHORT).show();

                    // ui
                    alertFab.setVisibility(View.GONE);
                    chainageSpinner.setVisibility(View.GONE);

                    fusedLocationProviderClient.removeLocationUpdates(locationCallback);

                    // end marker
                    endLocationMarker = mMap.addMarker(new MarkerOptions().position(latLngFromLocation(locations.get(locations.size() - 1))).title("End Location"));
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 150));

                    uploadTrip();
                    isTripStarted = false;
                }
            }
        });

        alertFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, ReportActivity.class);
                intent.putExtra("locations", locations);
                startActivity(intent);
            }
        });

        takeMeasurementFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, MeasurementActivity.class);

                if (isLocationReceived) {
                    intent.putExtra("location", locations.get(locations.size() - 1));
                }

                startActivity(intent);
            }
        });

        signOutFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAuth.signOut();
                startActivity(new Intent(MapsActivity.this, MainActivity.class));
            }
        });

        // spinner
        ArrayList<String> choices = getChainageSpinnerChoices();
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chainageSpinner.setAdapter(spinnerAdapter);

    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);

            locations.add(locationResult.getLastLocation());
            updateMap(locationResult.getLastLocation());

            if (!isLocationReceived) {
                isLocationReceived = true;
                isTripStarted = true;

                startStopTripFab.setTitle(getResources().getString(R.string.stop_button_text));
                alertFab.setEnabled(true);
                alertFab.setTitle(getResources().getString(R.string.report));
            }

            if (isTripDeviated(locationResult.getLastLocation()) && !isDeviationReportRecentlyMade) {
                Toast.makeText(MapsActivity.this, "Going off course", Toast.LENGTH_SHORT).show();

                makeDeviationReport(locationResult.getLastLocation());
            }

            updateDB(locations);
        }
    };

    private void updateDB(ArrayList<Location> currentLocations) {
//        HashMap<String, Object> updateMap = new HashMap<>();
        tripDetails.put("locations", currentLocations);

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date((long) tripDetails.get("startTime"))))

                .set(tripDetails, SetOptions.merge())

                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }

    private void makeDeviationReport(Location location) {
        HashMap<String, Object> deviationHashMap = new HashMap<>();
        deviationHashMap.put("reportTime", new Date().getTime());
        deviationHashMap.put("reportLocation", location);

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date((long) tripDetails.get("startTime"))))
                .collection("deviations")
                .document(String.valueOf(deviationHashMap.get("reportTime")))

                .set(deviationHashMap)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        isDeviationReportRecentlyMade = true;

                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isDeviationReportRecentlyMade = false;
                            }
                        }, DEVIATION_REPORT_INTERVAL);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }

    private void uploadTrip() {
        tripDetails.put("isOngoing", false);

        // get spinner selection
        ArrayList<String> choices = getChainageSpinnerChoices();
        if (!choices.get(chainageSpinner.getSelectedItemPosition()).equals(getResources().getString(R.string.enter_chainage_link))) {
            tripDetails.put("chainage", choices.get(chainageSpinner.getSelectedItemPosition()));
        }

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date((long) tripDetails.get("startTime"))))

                .set(tripDetails, SetOptions.merge())

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        resetMap();

                        startStopTripFab.setTitle(getResources().getString(R.string.start_button_text));

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

    private void updateMap(Location location) {
        if (locations.size() == 1) {
            // start marker
            startLocationMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())).title("Start Location"));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 16F));
        }
        tripPolylines.add(mMap.addPolyline(tripPolylineOptions.add(
                latLngFromLocation(locations.get(locations.size() - 1)),
                latLngFromLocation(location)
        )));
        cameraBounds.include(latLngFromLocation(location));
    }

    private void resetMap() {
        for (Polyline polyline : tripPolylines) {
            polyline.remove();
        }
        startLocationMarker.remove();
        endLocationMarker.remove();

        locations = new ArrayList<>();
        tripPolylines = new ArrayList<>();
        tripPolylineOptions = new PolylineOptions();
        cameraBounds = new LatLngBounds.Builder();
        tripDetails = new HashMap<>();
        isLocationReceived = false;  // sort of like first run, so set to false
    }

    private boolean isTripDeviated(Location location) {
        boolean deviated = true;
        for (LatLng pipelineLatLng : pipelineLatLngs) {
            float[] results = new float[1];
            Location.distanceBetween(pipelineLatLng.latitude, pipelineLatLng.longitude, location.getLatitude(), location.getLongitude(), results);

            if (results[0] < DEVIATION_THRESHOLD) {
                deviated = false;
                break;
            }
        }
        return deviated;
    }

    private LatLng latLngFromLocation(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    private ArrayList<String> getChainageSpinnerChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add(getResources().getString(R.string.enter_chainage_link));

        for (int start = CHAINAGE_START; start < CHAINAGE_END - CHAINAGE_INTERVAL; start += CHAINAGE_INTERVAL) {
            choices.add(start + "-" + (start + CHAINAGE_INTERVAL) + " kms");
        }

        return choices;
    }

    @Override
    protected void onStart() {
        super.onStart();

        mAuth = FirebaseAuth.getInstance();

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        String messagingToken = instanceIdResult.getToken();

                        db.collection("rmpWorkers")
                                .document(mAuth.getCurrentUser().getUid())
                                .update("token", messagingToken)

                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e(TAG, "onFailure: ", e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }
}

// todo: change distance for location req
// todo: add fab to view reports
// done: delet imagess