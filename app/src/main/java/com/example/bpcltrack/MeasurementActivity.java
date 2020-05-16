package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class MeasurementActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private static final long FASTEST_UPDATE_INTERVAL = 5000L;
    private static final long UPDATE_INTERVAL = 10000L;
    private static final double MIN_VOLT_INTERVAL = 0.01;
    private static final double NORMAL_PSP_VOLT = 1.16;
    private static final double NORMAL_AC_VOLT = 1.06;
    private static final int TLP_START = 0;
    private static final int TLP_END = 40;
    private static final String[] tlpTypes = new String[]{"Choose TLP Type…", "AJB", "BJB", "CJB"};
    private static final int REQUEST_IMAGE_CAPTURE = 1;

    private Location location;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private File imageFile;
    private Uri imageUri;
    private float pspValue, acValue;

    private Spinner tlpNumberSpinner, tlpTypeSpinner;
    private SeekBar pspSeekBar, acSeekBar;
    private TextView pspTextView, acTextView;
    private EditText remarksEditText;
    private ImageView imageView;
    private Button attachPictureButton, submitMeasurementButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        tlpNumberSpinner = findViewById(R.id.tlp_number_spinner);
        tlpTypeSpinner = findViewById(R.id.tlp_type_spinner);
        pspSeekBar = findViewById(R.id.psp_seek_bar);
        pspTextView = findViewById(R.id.psp_text_view);
        acSeekBar = findViewById(R.id.ac_seek_bar);
        acTextView = findViewById(R.id.ac_text_view);
        remarksEditText = findViewById(R.id.measurement_remarks_edit_text);
        imageView = findViewById(R.id.measurement_image_view);
        attachPictureButton = findViewById(R.id.measurement_take_picture_button);
        submitMeasurementButton = findViewById(R.id.submit_measurement_button);
        progressBar = findViewById(R.id.submit_measurement_progress_bar);

        progressBar.setVisibility(View.INVISIBLE);
        submitMeasurementButton.setText(getResources().getString(R.string.getting_location));
        submitMeasurementButton.setEnabled(false);
        attachPictureButton.setText(getString(R.string.attach_picture));

        // set up seekbar
        pspSeekBar.setProgress(pspSeekBar.getMax() / 2);
        acSeekBar.setProgress(acSeekBar.getMax() / 2);
        String setText = NORMAL_PSP_VOLT + "V";
        pspTextView.setText(setText);
        pspValue = (float) NORMAL_PSP_VOLT;
        setText = NORMAL_AC_VOLT + "V";
        acTextView.setText(setText);
        acValue = (float) NORMAL_AC_VOLT;

        pspSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = (float) (NORMAL_PSP_VOLT + ((((double) progress) -
                        ((double) ((double) (seekBar.getMax()) / 2.0D))) * MIN_VOLT_INTERVAL));  // !!!!!!
                pspValue = val;
                Log.d(TAG, "onProgressChanged: " + pspValue);
                String setText = val + "V";
                pspTextView.setText(setText);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        acSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = (float) (NORMAL_AC_VOLT + ((((double) progress) -
                        ((double) ((double) ((double) seekBar.getMax()) / ((double) 2.0)))) * ((double) MIN_VOLT_INTERVAL)));  // !!!! BigDecimal
                acValue = val;
                Log.d(TAG, "onProgressChanged: " + acValue);
                String setText = val + "V";
                acTextView.setText(setText);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // spinner set up
        ArrayAdapter<String> tlpNumberSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getTlpNumberSpinnerChoices());
        tlpNumberSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tlpNumberSpinner.setAdapter(tlpNumberSpinnerAdapter);

        ArrayAdapter<String> tlpTypesSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tlpTypes);
        tlpTypesSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tlpTypeSpinner.setAdapter(tlpTypesSpinnerAdapter);

        attachPictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dispatchTakePictureIntent();
            }
        });

        submitMeasurementButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitMeasurement();
            }
        });

        // location
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("location")) {
            location = (Location) getIntent().getExtras().get("location");

            gotLocation();
        } else {
            Dexter.withContext(this)
                    .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
                    .withListener(new MultiplePermissionsListener() {
                        @Override
                        public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MeasurementActivity.this);

                            locationRequest = new LocationRequest();
                            locationRequest.setInterval(UPDATE_INTERVAL);
                            locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL);
                            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                            fusedLocationProviderClient.requestLocationUpdates(
                                    locationRequest,
                                    locationCallback,
                                    MeasurementActivity.this.getMainLooper()
                            );
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                            permissionToken.continuePermissionRequest();
                        }
                    })
                    .check();
        }
    }

    private void submitMeasurement() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        StorageReference root = FirebaseStorage.getInstance().getReference();

        progressBar.setVisibility(View.VISIBLE);
        String measurementTime = String.valueOf(new Date().getTime());

        final HashMap<String, Object> measurement = new HashMap<>();
        measurement.put("location", location);
        measurement.put("uid", auth.getCurrentUser().getUid());
        measurement.put("by", auth.getCurrentUser().getEmail());
        measurement.put("measurementTime", measurementTime);
        if (tlpNumberSpinner.getSelectedItemPosition() != 0) {
            measurement.put("tlpNumber", getTlpNumberSpinnerChoices().get(tlpNumberSpinner.getSelectedItemPosition()));
        }
        if (tlpTypeSpinner.getSelectedItemPosition() != 0) {
            measurement.put("tlpType", tlpTypes[tlpTypeSpinner.getSelectedItemPosition()]);
        }
        measurement.put("pspValue", String.valueOf(pspValue));  // String.valueOf to compensate for float/double precision problem
        measurement.put("acValue", String.valueOf(acValue));  // String.valueOf to compensate for float/double precision problem
        measurement.put("remarks", remarksEditText.getText().toString());

        final StorageReference imageRef = root.child(auth.getCurrentUser().getEmail())
                .child("measurements")
                .child(measurementTime);
        imageRef.putFile(
                imageUri,
                new StorageMetadata.Builder()
                        .setCustomMetadata(
                                "remarks",
                                remarksEditText.getText().toString()
                        )
                        .build()
        ).continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
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
                measurement.put("imageUrl", uri.toString());

                updateDBWithMeasurement(measurement);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, "onFailure: ", e);
            }
        });
    }

    private void updateDBWithMeasurement(HashMap<String, Object> measurement) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("measurements")
                .document((String) measurement.get("measurementTime"))
                .set(measurement)

                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        if (!imageFile.delete()) {
                            Log.e(TAG, "onSuccess: IMAGE NOT DELETED");
                        }

                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(MeasurementActivity.this, "Measurement Recorded", Toast.LENGTH_SHORT).show();
                        MeasurementActivity.this.finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = new File(this.getFilesDir(), "Measurement" + new Date().getTime() + ".jpg");
            String authorities = getApplicationContext().getPackageName() + ".fileprovider";
            Uri photoURI = FileProvider.getUriForFile(this, authorities, photoFile);

            imageFile = photoFile;
            imageUri = photoURI;

            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void gotLocation() {
        submitMeasurementButton.setText(getResources().getString(R.string.stop_button_text));
        submitMeasurementButton.setEnabled(true);
    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            // todo: update ui

            location = locationResult.getLastLocation();
            gotLocation();
        }
    };

    private ArrayList<String> getTlpNumberSpinnerChoices() {
        ArrayList<String> tlpNumberChoices = new ArrayList<>();
        tlpNumberChoices.add(getString(R.string.choose_tlp_number));

        for (int i = TLP_START; i <= TLP_END; i++) {
            tlpNumberChoices.add("SV-" + i);
        }

        return tlpNumberChoices;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getPath());

            imageView.setImageBitmap(bitmap);
            imageView.setVisibility(View.VISIBLE);

            attachPictureButton.setText(getString(R.string.retake_picture));
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}

// todo: delet files after upload
