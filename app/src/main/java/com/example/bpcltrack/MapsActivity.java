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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

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
    private static final long REPORT_INTERVAL = 1 * 60 * 1000;
    private static final int CHAINAGE_INTERVAL = 10;
    private static final int CHAINAGE_START = 0;
    private static final int CHAINAGE_END = 600;

    private boolean isTripStarted = false;
    private boolean isReportRecentlyMade = false;
    private boolean isFirstLocationRecieved = false;
    private boolean firstDBUpdate = true;

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationProviderClient;
    protected GoogleMap mMap;
    private PolylineOptions tripPolyLineOptions;
    private LatLngBounds.Builder cameraBounds;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth mAuth;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    protected ProgressBar progressBar;
//    private Button startStopTrip, alertButton;
    private FloatingActionButton startStopTripFab, alertFab;
    private SupportMapFragment mapFragment;
    private Spinner chainageSpinner;

    private ArrayList<Location> locations;
    private ArrayList<HashMap<String, Object>> deviationHashMaps;
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
//        startStopTrip = findViewById(R.id.start_stop_trip);
//        alertButton = findViewById(R.id.alert_button);
        startStopTripFab = findViewById(R.id.start_stop_trip_fab);
        chainageSpinner = findViewById(R.id.chainage_spinner);
        alertFab = findViewById(R.id.alert_fab);
        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.map);

        progressBar.setVisibility(View.VISIBLE);
        alertFab.setVisibility(View.GONE);
        chainageSpinner.setVisibility(View.GONE);

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

        // todo
        startStopTripFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTripStarted) {
                    Toast.makeText(MapsActivity.this, "Trip started", Toast.LENGTH_SHORT).show();

                    locations = new ArrayList<>();
                    deviationHashMaps = new ArrayList<>();  // update map w deviations
                    tripDetails = new HashMap<>();
                    tripDetails.put("startTime", new Date().getTime());
//                    tripDetails.put("isOngoing", true);

                    fusedLocationProviderClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            MapsActivity.this.getMainLooper()
                    );

//                    alertButton.setEnabled(false);
//                    alertButton.setVisibility(View.VISIBLE);
//                    alertFab.setText(R.string.getting_location);
                    alertFab.setEnabled(false);
                    alertFab.setTitle(getResources().getString(R.string.getting_location));
                    alertFab.setVisibility(View.VISIBLE);
                    chainageSpinner.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(MapsActivity.this, "Trip Completed, Uploading...", Toast.LENGTH_SHORT).show();

                    fusedLocationProviderClient.removeLocationUpdates(locationCallback);

                    tripDetails.put("endTime", new Date().getTime());
                    tripDetails.put("isOngoing", false);  // todo: some error might be here

//                    startStopTrip.setText(R.string.start_button_text);
//                    alertButton.setVisibility(View.INVISIBLE);
                    startStopTripFab.setTitle(getResources().getString(R.string.start_button_text));
                    alertFab.setVisibility(View.GONE);
                    chainageSpinner.setVisibility(View.GONE);

                    // end marker
                    endLocationMarker = mMap.addMarker(new MarkerOptions().position(latLngFromLocation(locations.get(locations.size() - 1))).title("End Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 150));

                    // get spinner selection
                    ArrayList<String> choices = getChainageSpinnerChoices();
                    if (!choices.get(chainageSpinner.getSelectedItemPosition()).equals(getResources().getString(R.string.enter_chainage_link))) {
                        tripDetails.put("chainage", choices.get(chainageSpinner.getSelectedItemPosition()));
                    }

                    uploadTrip();
                    isTripStarted = false;
                }
            }
        });

        // todo
        alertFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MapsActivity.this, ReportActivity.class);
                intent.putExtra("locations", locations);
                startActivity(intent);
            }
        });

        // spinner
        ArrayList<String> choices = getChainageSpinnerChoices();
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chainageSpinner.setAdapter(spinnerAdapter);
    }

    private ArrayList<String> getChainageSpinnerChoices() {
        ArrayList<String> choices = new ArrayList<>();
        choices.add(getResources().getString(R.string.enter_chainage_link));

        for (int start = CHAINAGE_START; start < CHAINAGE_END - CHAINAGE_INTERVAL; start += CHAINAGE_INTERVAL) {
            choices.add(start + "-" + (start + CHAINAGE_INTERVAL) + " kms");
        }

        return choices;
    }

    private void loadPipelines() {
        LoadKml loadKml = new LoadKml(this, true, false, true);
        loadKml.execute();
    }

    private void uploadTrip() {
//        tripDetails.put("locations", locations);

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date((long) tripDetails.get("startTime"))))

                .set(tripDetails, SetOptions.merge())

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

            locations.add(locationResult.getLastLocation());
            updateMap(locationResult.getLastLocation());

            if (!isFirstLocationRecieved) {
                isFirstLocationRecieved = true;

//                alertButton.setVisibility(View.VISIBLE);
//                alertButton.setEnabled(true);
//                alertButton.setText(R.string.report);
                alertFab.setVisibility(View.VISIBLE);
                chainageSpinner.setVisibility(View.VISIBLE);
                alertFab.setEnabled(true);
                alertFab.setTitle(getResources().getString(R.string.report));
            }
            if (isTripDeviated(locationResult.getLastLocation()) && !isReportRecentlyMade) {
                // todo: notification
                Toast.makeText(MapsActivity.this, "Going off course", Toast.LENGTH_SHORT).show();

                HashMap<String, Object> deviationHashMap = new HashMap<>();
                deviationHashMap.put("reportTime", new Date().getTime());
                deviationHashMap.put("reportLocation", locationResult.getLastLocation());
                deviationHashMaps.add(deviationHashMap);
                makeDeviationReport(locationResult.getLastLocation());
            }

            updateDB(locations, deviationHashMaps);
        }
    };

    private void makeDeviationReport(Location lastLocation) {
    }

    private void updateDB(ArrayList<Location> currentLocations, ArrayList<HashMap<String, Object>> currentDeviationHashMaps) {
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("locations", currentLocations);
        updateMap.put("deviations", currentDeviationHashMaps);
        if (firstDBUpdate) {
            updateMap.put("startTime", tripDetails.get("startTime"));
            updateMap.put("isOngoing", true);
            updateMap.put("by", mAuth.getCurrentUser().getEmail());
            firstDBUpdate = false;
            isTripStarted = true;
//            startStopTrip.setText(R.string.stop_button_text);
            startStopTripFab.setTitle(getResources().getString(R.string.stop_button_text));
        }

        db.collection("rmpWorkers")
                .document(mAuth.getCurrentUser().getUid())
                .collection("trips")
                .document(simpleDateFormat.format(new Date((long) tripDetails.get("startTime"))))

                .set(updateMap, SetOptions.merge())
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

//    private void makeDeviationReport(Location lastLocation) {
//        HashMap<String, Object> map = new HashMap<>();
//
//        map.put("locations", locations);
//        map.put("lastLocation", lastLocation);
//        map.put("reportTime", new Date().getTime());
//        map.put("uid", mAuth.getCurrentUser().getUid());
//
//        db.collection("deviationReports")
//                .document(mAuth.getCurrentUser().getUid() + " " + simpleDateFormat.format(new Date()))
//
//                .set(map)
//
//                .addOnSuccessListener(new OnSuccessListener<Void>() {
//                    @Override
//                    public void onSuccess(Void aVoid) {
//                        isReportRecentlyMade = true;
//
//                        Handler handler = new Handler();
//                        handler.postDelayed(new Runnable() {
//                            @Override
//                            public void run() {
//                                isReportRecentlyMade = false;
//                            }
//                        }, REPORT_INTERVAL);
//                    }
//                })
//                .addOnFailureListener(new OnFailureListener() {
//                    @Override
//                    public void onFailure(@NonNull Exception e) {
//                        Log.e(TAG, "onFailure: ", e);
//                    }
//                });
//    }

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
// todo: add fab to view reports
// todo: delet imagess