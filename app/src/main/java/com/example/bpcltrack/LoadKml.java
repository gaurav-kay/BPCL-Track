package com.example.bpcltrack;

import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class LoadKml extends AsyncTask<Void, Void, PolylineOptions> {
    private static final String TAG = "TAG";

    private WeakReference<MapsActivity> mapsActivityWeakReference;
    private LatLngBounds.Builder cameraBounds = new LatLngBounds.Builder();

    public LoadKml(MapsActivity activity) {
        this.mapsActivityWeakReference = new WeakReference<>(activity);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        MapsActivity activity = mapsActivityWeakReference.get();
        activity.progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected PolylineOptions doInBackground(Void... voids) {
        MapsActivity activity = mapsActivityWeakReference.get();

        PolylineOptions polylineOptions = new PolylineOptions().color(Color.RED);

        InputStream inputStream = activity.getResources().openRawResource(R.raw.pipelines_csv);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
        String line;
        boolean firstLine = true;
        try {
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] row = line.split(",");

                cameraBounds.include(new LatLng(Double.parseDouble(row[1]), Double.parseDouble(row[0])));
                polylineOptions.add(new LatLng(Double.parseDouble(row[1]), Double.parseDouble(row[0])));
            }
        } catch (IOException e) {
            Log.d(TAG, "doInBackground: " + e);
        }

        return polylineOptions;
    }

    @Override
    protected void onPostExecute(PolylineOptions polylineOptions) {
        super.onPostExecute(polylineOptions);

        MapsActivity activity = mapsActivityWeakReference.get();

        activity.pipelineLatLngs = (ArrayList<LatLng>) polylineOptions.getPoints();
        activity.mMap.addPolyline(polylineOptions);
        activity.progressBar.setVisibility(View.INVISIBLE);
        activity.mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(this.cameraBounds.build(), 150));
    }
}
