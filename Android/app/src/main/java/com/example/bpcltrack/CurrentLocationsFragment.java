package com.example.bpcltrack;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

public class CurrentLocationsFragment extends Fragment {

    private static final String TAG = "TAG";

    private CurrentTripsAdapter adapter = null;

    private RecyclerView recyclerView;
    private TextView noCurrentTripsTextView;
    private ProgressBar progressBar;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_current_locations, container, false);
        MapsInitializer.initialize(view.getContext());

        noCurrentTripsTextView = view.findViewById(R.id.no_current_trips_text_view);
        recyclerView = view.findViewById(R.id.current_trips_recycler_view);
        progressBar = view.findViewById(R.id.current_trips_progress_bar);

        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView.setAdapter(null);

        noCurrentTripsTextView.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        db.collection("rmpWorkers")
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(final QuerySnapshot userDocumentSnapshots) {
                        final ArrayList<DocumentSnapshot> tripDocumentSnapshots = new ArrayList<>();

                        final int[] tripsQueried = {0};
                        for (DocumentSnapshot documentSnapshot : userDocumentSnapshots) {
                            Log.d(TAG, "onSuccess: " + documentSnapshot.getData());

                            documentSnapshot.getReference()
                                    .collection("trips")
                                    .whereEqualTo("isOngoing", true)
                                    .get()

                                    .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                                        @Override
                                        public void onSuccess(QuerySnapshot tripSnapshots) {
                                            Log.d(TAG, "onSuccess: " + tripSnapshots.size());
                                            tripsQueried[0]++;
                                            if (!tripSnapshots.isEmpty()) {
                                                tripDocumentSnapshots.add(tripSnapshots.getDocuments().get(0));
                                            }
                                            if (userDocumentSnapshots.size() == tripsQueried[0]) {
                                                setRecyclerView(tripDocumentSnapshots);
                                            }
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
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });

        return view;
    }

    private void setRecyclerView(ArrayList<DocumentSnapshot> tripDocumentSnapshots) {
        if (tripDocumentSnapshots.size() == 0) {
            String setText = "No worker tracking trip";
            noCurrentTripsTextView.setText(setText);
            noCurrentTripsTextView.setVisibility(View.VISIBLE);
        } else {
            ArrayList<HashMap<String, Object>> tripDocuments = new ArrayList<>();
            for (DocumentSnapshot tripDocumentSnapshot : tripDocumentSnapshots) {
                tripDocuments.add((HashMap<String, Object>) tripDocumentSnapshot.getData());
            }

            adapter = new CurrentTripsAdapter(tripDocuments);
            recyclerView.setAdapter(adapter);
        }
        progressBar.setVisibility(View.GONE);
    }

    private class CurrentTripsAdapter extends RecyclerView.Adapter<CurrentTripsAdapter.CurrentTripViewHolder> {

        ArrayList<HashMap<String, Object>> trips;

        public CurrentTripsAdapter(ArrayList<HashMap<String, Object>> trips) {
            this.trips = trips;
        }

        @NonNull
        @Override
        public CurrentTripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new CurrentTripViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout_current_trip, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final CurrentTripViewHolder holder, final int position) {
            Log.d(TAG, "onBindViewHolder: " + trips.get(position));

            if (trips.get(position).containsKey("by")) {  // db inconsistencies todo: fix
                String setText = String.valueOf(trips.get(position).get("by"));
                holder.userTextView.setText(setText);
            }

            holder.mapView.onCreate(null);
            holder.mapView.setClickable(false);
            holder.mapView.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    googleMap.getUiSettings().setMapToolbarEnabled(false);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);

                    AddDetailsAsync addDetailsAsync = new AddDetailsAsync(holder.mapView.getContext() ,(ArrayList) trips.get(position).get("locations"), googleMap);
                    addDetailsAsync.execute();
                }
            });
        }

        @Override
        public int getItemCount() {
            return trips.size();
        }

        public class CurrentTripViewHolder extends RecyclerView.ViewHolder {

            private TextView userTextView;
            private MapView mapView;

            public CurrentTripViewHolder(@NonNull View itemView) {
                super(itemView);

                userTextView = itemView.findViewById(R.id.current_trip_user_text_view);
                mapView = itemView.findViewById(R.id.current_trip_map_view);
            }
        }
    }

    private static class AddDetailsAsync extends AsyncTask<Void, Void, Void> {

        private ArrayList<HashMap<String, Object>> locations;

        private GoogleMap googleMap;
        private PolylineOptions polylineOptions;
        private MarkerOptions startMarkerOptions, currentMarkerOptions;
        private LatLngBounds.Builder cameraBounds;

        private WeakReference<Context> weakReference;

        public AddDetailsAsync(Context context, ArrayList<HashMap<String, Object>> locations, GoogleMap googleMap) {
            this.weakReference = new WeakReference<>(context);
            this.locations = locations;
            this.googleMap = googleMap;
            this.polylineOptions = new PolylineOptions();
            this.cameraBounds = new LatLngBounds.Builder();
            this.startMarkerOptions = new MarkerOptions();
            this.currentMarkerOptions = new MarkerOptions();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (HashMap hashMap : locations) {
                LatLng latLng = new LatLng(
                        Double.parseDouble(String.valueOf(hashMap.get("latitude"))),
                        Double.parseDouble(String.valueOf(hashMap.get("longitude")))
                );

                polylineOptions.add(latLng);
                cameraBounds.include(latLng);
            }

            startMarkerOptions.position(
                    new LatLng(
                            Double.parseDouble(String.valueOf(locations.get(0).get("latitude"))),
                            Double.parseDouble(String.valueOf(locations.get(0).get("longitude")))
                    )
            ).title("Start");

            currentMarkerOptions.position(
                    new LatLng(
                            Double.parseDouble(String.valueOf(locations.get(locations.size() - 1).get("latitude"))),
                            Double.parseDouble(String.valueOf(locations.get(locations.size() - 1).get("longitude")))
                    )
            ).title("Current Position").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            this.googleMap.addMarker(startMarkerOptions);
            this.googleMap.addMarker(currentMarkerOptions);
            this.googleMap.addPolyline(polylineOptions);
            this.googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(cameraBounds.build(), 0));
        }
    }
}
