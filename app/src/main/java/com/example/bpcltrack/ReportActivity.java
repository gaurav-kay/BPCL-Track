package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
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
    private ViewGroup linearLayout;
    private Spinner spinner;
    private NumberPicker priorityPicker;
    private EditText descriptionEditText;
    private ProgressBar progressBar;

    private StorageReference mStorageRef;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

    private ArrayList<Uri> photoFileUris;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        Bundle bundle = getIntent().getExtras();
        Log.d(TAG, "onCreate: " + bundle);
        final ArrayList<Location> locations = (ArrayList<Location>) bundle.get("locations");

        photoFileUris = new ArrayList<>();
        mStorageRef = FirebaseStorage.getInstance().getReference();

        spinner = findViewById(R.id.report_type_spinner);
        priorityPicker = findViewById(R.id.priority_picker);
        descriptionEditText = findViewById(R.id.report_description_edit_text);
        takePictureButton = findViewById(R.id.take_picture_button);
        linearLayout = findViewById(R.id.linear_layout);
        submitButton = findViewById(R.id.submit_report_button);
        progressBar = findViewById(R.id.submit_report_progress_bar);

        // spinner
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, REPORT_TYPES);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);

        // picker
        priorityPicker.setMinValue(0);
        priorityPicker.setMaxValue(PRIORITY_LEVELS.length - 1);
        priorityPicker.setValue(0);
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
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = new File(this.getFilesDir(), "Report" + new Date().getTime() + ".jpg");
            String authorities = getApplicationContext().getPackageName() + ".fileprovider";
            Uri photoURI = FileProvider.getUriForFile(this, authorities, photoFile);

            photoFileUris.add(photoURI);

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

        for (final Uri photoFileUri : photoFileUris) {
            final StorageReference imageRef = mStorageRef
                    .child(auth.getCurrentUser().getEmail())
                    .child(reportTime)  // or simpleDateFormat
                    .child(UUID.randomUUID().toString());
            UploadTask uploadTask = imageRef.putFile(
                    photoFileUri,
                    new StorageMetadata.Builder()
                            .setCustomMetadata(
                                    "description",
                                    descriptionEditText.getText().toString()
                            )
                            .build()
            );
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
                    map.put("imageUrl", uri);
                    map.put(
                            "description",
                            ((EditText) linearLayout.getChildAt(photoFileUris.indexOf(photoFileUri))
                            .findViewById(R.id.picture_edit_text)).getText().toString()
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

    private void updateDBWithReport(HashMap<String, Object> report) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("reports")
                .document((String) report.get("reportTime"))

                .set(report)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
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
            Bitmap bitmap = BitmapFactory.decodeFile(photoFileUris.get(photoFileUris.size() - 1).getPath());

//            ImageView imageView = new ImageView(ReportActivity.this);
//
//            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//            imageView.setImageBitmap(bitmap);
//            imageView.setLayoutParams(layoutParams);

            // todo: fix this shit

            View imageView = View.inflate(this, R.layout.item_layout_image, null);

            ((ImageView) imageView.findViewById(R.id.picture_image_view)).setImageBitmap(bitmap);

            linearLayout.addView(imageView);
        }
    }
}
