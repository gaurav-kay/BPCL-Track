package com.example.bpcltrack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.iid.FirebaseInstanceId;

public class MainActivity extends AppCompatActivity {
    // do auth and send to maps for workers and send to view notifs for officers
    private static final String TAG = "TAG";

    private Button signInButton;
    private EditText emailEditText, passwordEditText;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
//
//        Dexter.withContext(this)
//                .withPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
//                .withListener(new MultiplePermissionsListener() {
//                    @Override
//                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
//
//                    }
//
//                    @Override
//                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
//
//                    }
//                });

        emailEditText = findViewById(R.id.email_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);

        signInButton = findViewById(R.id.sign_in_button);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (emailEditText.getText().toString().trim().equals("") || passwordEditText.getText().toString().equals("")) {
                    Toast.makeText(MainActivity.this, "Enter both email and password", Toast.LENGTH_SHORT).show();
                } else {
                    findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
                    mAuth.signInWithEmailAndPassword(emailEditText.getText().toString(), passwordEditText.getText().toString())
                            .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                                @Override
                                public void onSuccess(AuthResult authResult) {
                                    findViewById(R.id.progress_bar).setVisibility(View.GONE);

                                    if (isRMPWorker(authResult.getUser())) {
                                        startActivity(new Intent(MainActivity.this, MapsActivity.class));
                                    } else {
                                        startActivity(new Intent(MainActivity.this, OfficerActivity.class));
                                    }
                                }
                            })
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    findViewById(R.id.progress_bar).setVisibility(View.GONE);
                                    Log.d(TAG, "onFailure: " + e);
                                }
                            });
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // custom claims to update ui
        // for now, db call

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            if (isRMPWorker(currentUser)) {
                startActivity(new Intent(MainActivity.this, MapsActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, OfficerActivity.class));
            }
        }
    }

    protected static boolean isRMPWorker(FirebaseUser currentUser) {
        // todo: enhance

        return currentUser.getEmail().startsWith("w");
    }
}
