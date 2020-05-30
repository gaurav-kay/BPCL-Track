package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class TripViewActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private DocumentReference tripDocumentReference;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    protected GoogleMap mMap;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    private SupportMapFragment mapFragment;
    private TextView headingTextView, subHeadingTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_view);

        tripDocumentReference = db.document(String.valueOf(getIntent().getExtras().get("tripDocumentPath")));

        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.trip_map);
        headingTextView = findViewById(R.id.trip_trip_summary);
        subHeadingTextView = findViewById(R.id.trip_trip_sub_summary);

        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                tripDocumentReference
                        .get()
                        .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(final DocumentSnapshot tripDocumentSnapshot) {
                                tripDocumentReference
                                        .collection("deviations")
                                        .get()

                                        .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                            @Override
                                            public void onSuccess(QuerySnapshot deviationQueryDocumentSnapshots) {
                                                ArrayList<DocumentSnapshot> deviations = new ArrayList<>();

                                                for (DocumentSnapshot deviationDocumentSnapshot : deviationQueryDocumentSnapshots) {
                                                    deviations.add(deviationDocumentSnapshot);
                                                }

                                                if (tripDocumentSnapshot.exists()) {
                                                    String setText = "";
                                                    if (tripDocumentSnapshot.contains("mapName")) {
                                                        setText += tripDocumentSnapshot.get("mapName");
                                                    } else {
                                                        setText += MapsActivity.DEFAULT_MAP;
                                                    }
                                                    setText += "\n";
                                                    if (tripDocumentSnapshot.contains("by")) {
                                                        setText += "Trip by: " + String.valueOf(tripDocumentSnapshot.get("by"));
                                                    }
                                                    headingTextView.setText(setText);

                                                    setText = "Trip started at " +
                                                            simpleDateFormat.format(new Date(Long.parseLong(String.valueOf(tripDocumentSnapshot.get("startTime")))))
                                                            + "\n" +
                                                            "Trip ended at " +
                                                            simpleDateFormat.format(new Date(Long.parseLong(String.valueOf(tripDocumentSnapshot.get("endTime")))));
                                                    if (tripDocumentSnapshot.contains("chainage")) {
                                                        setText += "\n" +
                                                                "Chainage: " +
                                                                tripDocumentSnapshot.get("chainage");
                                                    }
                                                    subHeadingTextView.setText(setText);

                                                    loadTrip(tripDocumentSnapshot.getData(), deviations);
                                                }
                                            }
                                        })
                                        .addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                Log.e(TAG, "onFailure: ", e);
                                            }
                                        });
                                MapsActivity.loadPipelinesMap(
                                        String.valueOf(tripDocumentSnapshot.get("mapName")),
                                        TripViewActivity.this,
                                        false,
                                        false,
                                        true
                                );
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "onFailure: ", e);
                            }
                        });
            }
        });
    }

    private void loadTrip(Map<String, Object> data, ArrayList<DocumentSnapshot> deviations) {
        LatLngBounds.Builder cameraBounds = new LatLngBounds.Builder();

        ArrayList<HashMap<String, Object>> locations = (ArrayList<HashMap<String, Object>>) data.get("locations");
        PolylineOptions polylineOptions = new PolylineOptions();
        for (HashMap<String, Object> location : locations) {
            polylineOptions.add(new LatLng(
                    Double.parseDouble(String.valueOf(location.get("latitude"))),
                    Double.parseDouble(String.valueOf(location.get("longitude")))
            ));
            cameraBounds.include(new LatLng(
                    Double.parseDouble(String.valueOf(location.get("latitude"))),
                    Double.parseDouble(String.valueOf(location.get("longitude")))
            ));
        }
        mMap.addPolyline(polylineOptions);

        mMap.addMarker(new MarkerOptions().title("Start Location").position(new LatLng(
                Double.parseDouble(String.valueOf(locations.get(0).get("latitude"))),
                Double.parseDouble(String.valueOf(locations.get(0).get("longitude")))
        )));
        mMap.addMarker(new MarkerOptions().title("End Location").position(new LatLng(
                Double.parseDouble(String.valueOf(locations.get(locations.size() - 1).get("latitude"))),
                Double.parseDouble(String.valueOf(locations.get(locations.size() - 1).get("longitude")))
        )));

        if (deviations.size() != 0) {
//            ArrayList<HashMap<String, Object>> deviationHashMaps = (ArrayList<HashMap<String, Object>>) data.get("deviations");
//            for (HashMap<String, Object> deviationHashMap : deviationHashMaps) {
//                Date date = new Date(Long.parseLong(String.valueOf(deviationHashMap.get("reportTime"))));
//                HashMap<String, Object> deviationLocation = (HashMap<String, Object>) deviationHashMap.get("reportLocation");
//
//                String title = "Deviated at " + simpleDateFormat.format(date);
//                mMap.addMarker(new MarkerOptions().title(title).position(new LatLng(
//                        Double.parseDouble(String.valueOf(deviationLocation.get("latitude"))),
//                        Double.parseDouble(String.valueOf(deviationLocation.get("longitude")))
//                )).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
//                cameraBounds.include(new LatLng(
//                        Double.parseDouble(String.valueOf(deviationLocation.get("latitude"))),
//                        Double.parseDouble(String.valueOf(deviationLocation.get("longitude")))
//                ));
//            }

            for (DocumentSnapshot deviationDocumentSnapshot : deviations) {
                Date date = new Date(Long.parseLong(String.valueOf(deviationDocumentSnapshot.get("reportTime"))));
                HashMap<String, Object> deviationLocation = (HashMap<String, Object>) deviationDocumentSnapshot.get("reportLocation");

                String title = "Deviated at " + simpleDateFormat.format(date);
                mMap.addMarker(new MarkerOptions().title(title).position(new LatLng(
                        Double.parseDouble(String.valueOf(deviationLocation.get("latitude"))),
                        Double.parseDouble(String.valueOf(deviationLocation.get("longitude")))
                )).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
                cameraBounds.include(new LatLng(
                        Double.parseDouble(String.valueOf(deviationLocation.get("latitude"))),
                        Double.parseDouble(String.valueOf(deviationLocation.get("longitude")))
                ));
            }
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 150));
    }
}
