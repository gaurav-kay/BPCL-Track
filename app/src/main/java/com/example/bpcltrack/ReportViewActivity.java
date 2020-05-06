package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class ReportViewActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private SupportMapFragment mapFragment;
    private RecyclerView recyclerView;
    private TextView reportHeadingTextView, reportTypeTextView, reportPriorityTextView, reportDescriptionTextView;
    private Button acknowledgeButton;
    private ProgressBar progressBar;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    protected GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_view);

        final HashMap<String, Object> report = (HashMap<String, Object>) getIntent().getSerializableExtra("report");

        mapFragment = (SupportMapFragment) this.getSupportFragmentManager().findFragmentById(R.id.report_view_map);
        recyclerView = findViewById(R.id.images_recycler_view);
        reportHeadingTextView = findViewById(R.id.report_main_heading_text_view);
        reportTypeTextView = findViewById(R.id.report_report_type_text_view);  // same heading
        reportPriorityTextView = findViewById(R.id.report_report_priority_text_view);
        reportDescriptionTextView = findViewById(R.id.report_description_text_view);
        acknowledgeButton = findViewById(R.id.acknowledge_button);
        progressBar = findViewById(R.id.updating_progress_bar);

        // setting texts of text views
        String setText = "Report by " + report.get("by") + " at " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(new Date(Long.parseLong(String.valueOf(report.get("reportTime")))));
        reportHeadingTextView.setText(setText);

        setText = "Report Type: " + report.get("reportType");
        reportTypeTextView.setText(setText);

        setText = "Priority: " + report.get("priority");
        reportPriorityTextView.setText(setText);

        setText = String.valueOf(report.get("description"));
        reportDescriptionTextView.setText(setText);

        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
//                mMap.setMyLocationEnabled(true);  // requires permission on officer side
//                mMap.getUiSettings().setMyLocationButtonEnabled(true);

                double latitude = Double.parseDouble(String.valueOf(((HashMap) report.get("reportLocation")).get("latitude")));
                double longitude = Double.parseDouble(String.valueOf(((HashMap) report.get("reportLocation")).get("longitude")));
                String reportType = String.valueOf(report.get("reportType"));
                mMap.addMarker(new MarkerOptions().position(new LatLng(latitude, longitude)).title(reportType));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 13F));
                mMap.getUiSettings().setMapToolbarEnabled(false);
                loadPipelines();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setAdapter(new ImagesAdapter((ArrayList<HashMap<String, Object>>) report.get("images")));

        acknowledgeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);
                db.collection("reports")
                        .document(String.valueOf(report.get("reportTime")))

                        .update("acknowledged", !Boolean.parseBoolean(String.valueOf(report.get("acknowledged"))))

                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(ReportViewActivity.this, "Acknowledged", Toast.LENGTH_SHORT).show();
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

    private void loadPipelines() {
        LoadKml loadKml = new LoadKml(this, false);
        loadKml.execute();
    }

    private class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.ImageViewHolder> {

        private ArrayList<HashMap<String, Object>> images;

        ImagesAdapter(ArrayList<HashMap<String, Object>> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ImageViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout_image_report_view, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull final ImageViewHolder holder, int position) {
            Glide.with(holder.imageView.getContext())
                    .load(String.valueOf(images.get(position).get("imageUrl")))
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "onLoadFailed: ", e);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            holder.imageView.setVisibility(View.VISIBLE);
                            holder.progressBar.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.imageView);
            holder.textView.setText(String.valueOf(images.get(position).get("description")));
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        public class ImageViewHolder extends RecyclerView.ViewHolder {

            private ImageView imageView;
            private TextView textView;
            private ProgressBar progressBar;

            public ImageViewHolder(@NonNull View itemView) {
                super(itemView);

                progressBar = itemView.findViewById(R.id.image_loading_progress_bar);
                imageView = itemView.findViewById(R.id.picture_image_view);  // same id
                textView = itemView.findViewById(R.id.picture_text_view);
            }
        }
    }
}
