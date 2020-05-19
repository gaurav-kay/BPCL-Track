package com.example.bpcltrack;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class AllTripsFragment extends Fragment {
    private static final String TAG = "TAG";

    private ListView listView;
    private ProgressBar progressBar;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseAuth auth;

    private ArrayList<String> rmpWorkers;
    private ArrayList<DocumentReference> rmpWorkerDocumentReferences;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_all_trips, container, false);

        auth = FirebaseAuth.getInstance();
        rmpWorkerDocumentReferences = new ArrayList<>();
        rmpWorkers = new ArrayList<>();

        listView = view.findViewById(R.id.all_trips_rmp_workers_list_view);
        progressBar = view.findViewById(R.id.all_trips_rmp_workers_loading_progress_bar);

        listView.setAdapter(null);
        progressBar.setVisibility(View.VISIBLE);

        db.collection("rmpWorkers")
                .get()

                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        rmpWorkers = new ArrayList<>();
                        rmpWorkerDocumentReferences = new ArrayList<>();
                        for (DocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                            rmpWorkers.add(String.valueOf(documentSnapshot.getData().get("email")));
                            rmpWorkerDocumentReferences.add(documentSnapshot.getReference());
                        }

                        ArrayAdapter adapter = new ArrayAdapter<>(view.getContext(), android.R.layout.simple_list_item_1, rmpWorkers);
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
                Intent intent = new Intent(getActivity(), TripsListViewActivity.class);

                intent.putExtra("rmpWorkerDocumentPath", rmpWorkerDocumentReferences.get(position).getPath());
                startActivity(intent);
            }
        });

        return view;
    }
}
