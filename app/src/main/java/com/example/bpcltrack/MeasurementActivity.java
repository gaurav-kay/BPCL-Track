package com.example.bpcltrack;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

public class MeasurementActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private static final long FASTEST_UPDATE_INTERVAL = 5000L;
    private static final long UPDATE_INTERVAL = 10000L;
    private static final double MIN_VOLT_INTERVAL = 0.01;
    private static final double NORMAL_PSP_VOLT = 1.16;
    private static final double NORMAL_AC_VOLT = 1.06;

    private Location location;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;

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

        // set up seekbar
        pspSeekBar.setProgress(pspSeekBar.getMax() / 2);
        acSeekBar.setProgress(acSeekBar.getMax() / 2);
        String setText = NORMAL_PSP_VOLT + "V";
        pspTextView.setText(setText);
        setText = NORMAL_AC_VOLT + "V";
        acTextView.setText(setText);

        pspSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = (float) (NORMAL_PSP_VOLT + ((((double) progress) -
                        ((double) ((double) (seekBar.getMax()) / 2.0D))) * MIN_VOLT_INTERVAL));  // !!!!!!
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
                String setText = val + "V";
                acTextView.setText(setText);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
