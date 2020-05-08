package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
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
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private SupportMapFragment mapFragment;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_view);

        tripDocumentReference = db.document(String.valueOf(getIntent().getExtras().get("tripDocumentPath")));
        Log.wtf(TAG, "onCreate: " + tripDocumentReference.getId());

        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.trip_map);
        textView = findViewById(R.id.trip_trip_summary);

        ViewGroup.LayoutParams params = mapFragment.getView().getLayoutParams();
        DisplayMetrics displaymetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        params.height = (int) (displaymetrics.heightPixels * 0.8);
        mapFragment.getView().setLayoutParams(params);

        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
                tripDocumentReference.get()
                        .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                            @Override
                            public void onSuccess(DocumentSnapshot documentSnapshot) {
                                if (documentSnapshot.exists()) {
                                    String setText = "";
                                    if (documentSnapshot.contains("by")) {
                                        setText += "Trip by: " + String.valueOf(documentSnapshot.get("by"))
                                                + "\n\n";
                                    }
                                    setText += "Trip started at " +
                                            simpleDateFormat.format(new Date(Long.parseLong(String.valueOf(documentSnapshot.get("startTime")))))
                                            + "\n" +
                                            "Trip ended at " +
                                            simpleDateFormat.format(new Date(Long.parseLong(String.valueOf(documentSnapshot.get("endTime")))));
                                    textView.setText(setText);
                                    loadTrip(documentSnapshot.getData());
                                }
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "onFailure: ", e);
                            }
                        });
                loadPipelines();
            }
        });
    }

    private void loadTrip(Map<String, Object> data) {
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

        if (data.containsKey("deviations")) {
            ArrayList<HashMap<String, Object>> deviationHashMaps = (ArrayList<HashMap<String, Object>>) data.get("deviations");
            for (HashMap<String, Object> deviationHashMap : deviationHashMaps) {
                Date date = new Date(Long.parseLong(String.valueOf(deviationHashMap.get("reportTime"))));
                HashMap<String, Object> deviationLocation = (HashMap<String, Object>) deviationHashMap.get("reportLocation");

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

        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 150));
    }

    private void loadPipelines() {
        LoadKml loadKml = new LoadKml(this, false, false, true);
        loadKml.execute();
    }
}
