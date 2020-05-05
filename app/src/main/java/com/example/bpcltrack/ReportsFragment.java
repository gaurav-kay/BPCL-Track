package com.example.bpcltrack;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;

public class ReportsFragment extends Fragment {

    private static final String TAG = "TAG";

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ReportsAdapter reportsAdapter = null;

    private RecyclerView recyclerView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_reports, container, false);

        MapsInitializer.initialize(view.getContext());

        recyclerView = view.findViewById(R.id.reports_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext()));
        recyclerView.setAdapter(null);
        recyclerView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = recyclerView.getChildLayoutPosition(v);

                Intent intent = new Intent(getActivity(), ReportViewActivity.class);
                intent.putExtra("report", (Parcelable) reportsAdapter);

                startActivity();            }
        });

        db.collection("reports")
                .get()

                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        ArrayList<DocumentSnapshot> reportSnapshots = new ArrayList<>();

                        for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                            if (documentSnapshot.getId().split(" ").length == 1) {
                                reportSnapshots.add(documentSnapshot);
                                Log.d(TAG, "onSuccess: " + documentSnapshot);
                            }
                        }

                        reportsAdapter = new ReportsAdapter(reportSnapshots);
                        reportsAdapter.notifyDataSetChanged();

                        recyclerView.setAdapter(reportsAdapter);
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

    private class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ReportViewHolder> {

        private ArrayList<DocumentSnapshot> reportSnapshots;

        public ReportsAdapter(ArrayList<DocumentSnapshot> reportSnapshots) {
            this.reportSnapshots = reportSnapshots;
        }

        @NonNull
        @Override
        public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout_report, parent, false);

            return new ReportViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
            holder.reportTypeTextView.setText(String.valueOf(reportSnapshots.get(position).getData().get("reportType")));
            holder.reportPriorityTextView.setText(String.valueOf(reportSnapshots.get(position).getData().get("priority")));
            holder.reportDescriptionTextView.setText(String.valueOf(reportSnapshots.get(position).getData().get("description")));

            final double latitude = (double) ((HashMap) reportSnapshots.get(position).getData().get("reportLocation")).get("latitude");
            final double longitude = (double) ((HashMap) reportSnapshots.get(position).getData().get("reportLocation")).get("longitude");

            holder.mapView.onCreate(null);
            holder.mapView.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(GoogleMap googleMap) {
                    googleMap.getUiSettings().setMapToolbarEnabled(false);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                    googleMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)));
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(latitude, longitude)));
                }
            });
        }

        @Override
        public int getItemCount() {
            return reportSnapshots.size();
        }

        private class ReportViewHolder extends RecyclerView.ViewHolder {

            private MapView mapView;
            private TextView reportTypeTextView, reportPriorityTextView, reportDescriptionTextView;

            public ReportViewHolder(@NonNull View itemView) {
                super(itemView);

                mapView = itemView.findViewById(R.id.report_report_location);
                reportTypeTextView = itemView.findViewById(R.id.report_report_type_text_view);
                reportPriorityTextView = itemView.findViewById(R.id.report_report_priority_text_view);
                reportDescriptionTextView = itemView.findViewById(R.id.report_description_text_view);
            }
        }
    }
}
