package com.example.bpcltrack;

import android.app.Notification;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import static com.example.bpcltrack.App.DEVIATION_CHANNEL_ID;

public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "TAG";

    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> map = remoteMessage.getData();

            String title = map.get("by") + " has deviated from their Trip";
            String description = "Deviation recorded at " + simpleDateFormat.format(new Date(Long.parseLong(map.get("reportTime")))) + ". Click to View...";

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            Notification notification = new NotificationCompat.Builder(this, DEVIATION_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(description)
                    .setSmallIcon(R.drawable.ic_error_black_24dp)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .build();

            Log.d(TAG, "onMessageReceived: " + map);
            int id = Integer.parseInt(map.get("id"));
            notificationManager.notify(id, notification);
        }
    }

    @Override
    public void onNewToken(@NonNull final String token) {
        super.onNewToken(token);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            if (MainActivity.isRMPWorker(auth.getCurrentUser())) {
                db.collection("rmpWorkers")
                        .document(auth.getCurrentUser().getUid())
                        .update("token", token)

                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "onFailure: ", e);
                            }
                        });
            } else {
                db.collection("officers")
                        .document(auth.getCurrentUser().getUid())
                        .update("token", token)

                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "onFailure: ", e);
                            }
                        });
            }
        }
    }
}
