package com.example.bpcltrack;

import android.content.Context;
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

    private WeakReference<Context> weakReference;
    private boolean isMapsActivity, isReportViewActivity, isTripViewActivity;
    private LatLngBounds.Builder cameraBounds = new LatLngBounds.Builder();
    private InputStream inputStream;
    private int color;

    public LoadKml(Context context, boolean isMapsActivity, boolean isReportViewActivity, boolean isTripViewActivity, int resourceId, int color) {
        this.weakReference = new WeakReference<>(context);
        this.isMapsActivity = isMapsActivity;
        this.isReportViewActivity = isReportViewActivity;
        this.isTripViewActivity = isTripViewActivity;
        this.inputStream = weakReference.get().getResources().openRawResource(resourceId);
        this.color = color;
    }

    @Override
    protected PolylineOptions doInBackground(Void... voids) {
        PolylineOptions polylineOptions = new PolylineOptions().color(color);

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

        Context context = weakReference.get();
        if (isMapsActivity) {
            ((MapsActivity) context).pipelineLatLngs = (ArrayList<LatLng>) polylineOptions.getPoints();
//            ((MapsActivity) context).mMap.addPolyline(polylineOptions);
            ((MapsActivity) context).mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(this.cameraBounds.build(), 150));
            ((MapsActivity) context).progressBar.setVisibility(View.INVISIBLE);
        } else if (isReportViewActivity) {
            ((ReportViewActivity) context).mMap.addPolyline(polylineOptions);
        } else if (isTripViewActivity) {
            ((TripViewActivity) context).mMap.addPolyline(polylineOptions);
        }
    }
}

// todo: enhance
