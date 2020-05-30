package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class TripsListViewActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private DocumentReference rmpWorkerDocumentReference;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ListView listView;
    private ProgressBar progressBar;

    private ArrayList<DocumentReference> tripDocumentReferenes;
    private ArrayList<String> tripNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips_list_view);

        tripDocumentReferenes = new ArrayList<>();
        tripNames = new ArrayList<>();

        listView = findViewById(R.id.all_trips_view_trips_of_worker_list_view);
        progressBar = findViewById(R.id.all_trips_trips_loading_progress_bar);

        listView.setAdapter(null);
        progressBar.setVisibility(View.VISIBLE);

        rmpWorkerDocumentReference = db.document(String.valueOf(getIntent().getExtras().get("rmpWorkerDocumentPath")));

        rmpWorkerDocumentReference.collection("trips")
                .whereEqualTo("isOngoing", false)
                .get()

                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryTripDocumentSnapshots) {
                        tripDocumentReferenes = new ArrayList<>();
                        tripNames = new ArrayList<>();
                        for (DocumentSnapshot tripDocumentSnapshot : queryTripDocumentSnapshots) {
                            tripDocumentReferenes.add(tripDocumentSnapshot.getReference());
//                            String name = "Trip at " + tripDocumentSnapshot.getId();
                            StringBuilder name = new StringBuilder();
                            if (tripDocumentSnapshot.contains("mapName")) {
                                name.append(tripDocumentSnapshot.get("mapName")).append(" ");
                            } else {
                                name.append(MapsActivity.DEFAULT_MAP).append(" ");
                            }
                            name.append("trip at ").append(tripDocumentSnapshot.getId());
                            tripNames.add(name.toString());
                        }

                        ArrayAdapter adapter = new ArrayAdapter<>(TripsListViewActivity.this, android.R.layout.simple_list_item_1, tripNames);
                        listView.setAdapter(adapter);
                        progressBar.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(TripsListViewActivity.this, TripViewActivity.class);
                intent.putExtra("tripDocumentPath", tripDocumentReferenes.get(position).getPath());
                startActivity(intent);
            }
        });
    }
}
