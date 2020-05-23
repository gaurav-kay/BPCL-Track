package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ReportActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final String[] REPORT_TYPES =
            new String[]{"Excavation", "Pipe Damage", "Pipe Leak", "Maintenance Required", "Other"};
    private static final String[] PRIORITY_LEVELS =
            new String[]{"Low", "Medium", "High", "Severe"};

    private Button takePictureButton, submitButton;
    private RecyclerView imagesRecyclerView;
    private Spinner spinner;
    private NumberPicker priorityPicker;
    private EditText descriptionEditText;
    private ProgressBar progressBar;

    private StorageReference mStorageRef;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private ImagesAdapter imagesAdapter;

    private ArrayList<Uri> photoFileUris;
    private ArrayList<File> photoFiles;
    private ArrayList<Bitmap> photoBitmaps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        Bundle bundle = getIntent().getExtras();
        final ArrayList<Location> locations = (ArrayList<Location>) bundle.get("locations");

        photoFileUris = new ArrayList<>();
        photoFiles = new ArrayList<>();
        photoBitmaps = new ArrayList<>();
        mStorageRef = FirebaseStorage.getInstance().getReference();

        spinner = findViewById(R.id.report_type_spinner);
        priorityPicker = findViewById(R.id.priority_picker);
        descriptionEditText = findViewById(R.id.report_description_edit_text);
        takePictureButton = findViewById(R.id.take_picture_button);
        imagesRecyclerView = findViewById(R.id.images_recycler_view);
        submitButton = findViewById(R.id.submit_report_button);
        progressBar = findViewById(R.id.submit_report_progress_bar);

        // spinner
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, REPORT_TYPES);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        // picker
        priorityPicker.setMinValue(0);
        priorityPicker.setMaxValue(PRIORITY_LEVELS.length - 1);
        priorityPicker.setValue(1);
        priorityPicker.setWrapSelectorWheel(false);
        priorityPicker.setDisplayedValues(PRIORITY_LEVELS);

        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitReport(locations);
            }
        });

        imagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        imagesRecyclerView.setNestedScrollingEnabled(false);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = new File(this.getFilesDir(), "Report" + new Date().getTime() + ".jpg");
            String authorities = getApplicationContext().getPackageName() + ".fileprovider";
            Uri photoURI = FileProvider.getUriForFile(this, authorities, photoFile);

            photoFileUris.add(photoURI);
            photoFiles.add(photoFile);

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void submitReport(ArrayList<Location> locations) {
        progressBar.setVisibility(View.VISIBLE);

        final HashMap<String, Object> report = new HashMap<>();
        final ArrayList<HashMap<String, Object>> imageDescriptions = new ArrayList<>();

        final String reportTime = String.valueOf(new Date().getTime());
        final FirebaseAuth auth = FirebaseAuth.getInstance();

        report.put("uid", auth.getCurrentUser().getUid());
        report.put("priority", PRIORITY_LEVELS[priorityPicker.getValue()]);
        report.put("reportTime", reportTime);
        report.put("description", descriptionEditText.getText().toString());
        report.put("locations", locations);
        report.put("reportLocation", locations.get(locations.size() - 1));
        report.put("reportType", REPORT_TYPES[spinner.getSelectedItemPosition()]);
        report.put("by", auth.getCurrentUser().getEmail());
        report.put("acknowledged", false);

        if (imagesAdapter == null) {
            updateDBWithReport(report);
        } else {
            final ArrayList<String> descriptions = imagesAdapter.getDescriptions();

            for (int position = 0; position < photoFileUris.size(); position++) {
                final StorageReference imageRef = mStorageRef
                        .child(auth.getCurrentUser().getEmail())
                        .child(reportTime)  // or simpleDateFormat
                        .child(UUID.randomUUID().toString());
                UploadTask uploadTask = imageRef.putFile(
                        photoFileUris.get(position),
                        new StorageMetadata.Builder()
                                .setCustomMetadata(
                                        "description",
                                        descriptionEditText.getText().toString()
                                )
                                .build()
                );
                final int finalPosition = position;
                uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                    @Override
                    public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                        if (!task.isSuccessful()) {
                            Log.e(TAG, "then: ", task.getException());
                            throw Objects.requireNonNull(task.getException());
                        }

                        return imageRef.getDownloadUrl();
                    }
                }).addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        HashMap<String, Object> map = new HashMap<>();
                        map.put("imageUrl", uri.toString());
                        map.put(
                                "description",
                                descriptions.get(finalPosition)
                        );

                        imageDescriptions.add(map);
                        if (imageDescriptions.size() == photoFileUris.size()) {
                            report.put("images", imageDescriptions);
                            updateDBWithReport(report);
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
            }
        }
    }

    private void updateDBWithReport(HashMap<String, Object> report) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("reports")
                .document((String) report.get("reportTime"))

                .set(report)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // todo: add progress bar
                        for (int i = 0; i < photoFiles.size(); i++) {
                            if (!photoFiles.get(i).delete()) {
                                Log.e(TAG, "onSuccess: IMAGE FILE NOT DELETED");
                            }
                        }

                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ReportActivity.this, "Report Submitted", Toast.LENGTH_SHORT).show();
                        ReportActivity.this.finish();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            Bitmap bitmap = BitmapFactory.decodeFile(photoFiles.get(photoFiles.size() - 1).getPath());
            photoBitmaps.add(bitmap);

            imagesAdapter = new ImagesAdapter(photoBitmaps);
            imagesRecyclerView.setAdapter(imagesAdapter);
        }
    }

    private class ImagesAdapter extends RecyclerView.Adapter<ImagesAdapter.ImageViewHolder> {

        private ArrayList<Bitmap> imageBitmaps;
        private ArrayList<EditText> descriptionEditTexts;

        ImagesAdapter(ArrayList<Bitmap> imageBitmaps) {
            this.imageBitmaps = imageBitmaps;
            this.descriptionEditTexts = new ArrayList<>();
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout_image, parent, false);
            return new ImageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder imageViewHolder, final int position) {
//            imageViewHolder.pictureImageView.setImageBitmap(imageBitmaps.get(position));
            Glide.with(imageViewHolder.pictureImageView.getContext())
                    .load(imageBitmaps.get(position))
                    .into(imageViewHolder.pictureImageView);
            descriptionEditTexts.add(imageViewHolder.pictureDescription);
            imageViewHolder.pictureDescription.setText(descriptionEditTexts.get(position).getText());
        }

        @Override
        public int getItemCount() {
            return imageBitmaps.size();
        }

        public ArrayList<String> getDescriptions() {
            ArrayList<String> descriptions = new ArrayList<>();
            for (EditText editText : descriptionEditTexts) {
                descriptions.add(editText.getText().toString());
            }
            return descriptions;
        }

        private class ImageViewHolder extends RecyclerView.ViewHolder {

            private ImageView pictureImageView;
            private EditText pictureDescription;

            ImageViewHolder(@NonNull View itemView) {
                super(itemView);

                pictureImageView = itemView.findViewById(R.id.picture_image_view);
                pictureDescription = itemView.findViewById(R.id.picture_edit_text);
            }
        }
    }
}

// todo: on back press restart report
