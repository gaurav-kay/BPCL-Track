package com.example.bpcltrack;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class TripViewActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private DocumentReference tripDocumentReference;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_view);

        tripDocumentReference = db.document(String.valueOf(getIntent().getExtras().get("tripDocumentPath")));
        Log.wtf(TAG, "onCreate: " + tripDocumentReference.getId());
    }
}
