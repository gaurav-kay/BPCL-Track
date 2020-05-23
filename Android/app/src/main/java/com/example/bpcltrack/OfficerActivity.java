package com.example.bpcltrack;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

public class OfficerActivity extends AppCompatActivity {

    private static final String TAG = "TAG";

    private SectionsPageAdapter sectionsPageAdapter;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    private ViewPager viewPager;
    private TabLayout tabLayout;

    private final Fragment[] FRAGMENTS = new Fragment[]{new ReportsFragment(), new CurrentLocationsFragment(), new AllTripsFragment()};
    private final String[] TITLES = new String[]{"Reports", "Current Locations", "All Trips"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_officer);

        // set up viewpager
        sectionsPageAdapter = new SectionsPageAdapter(
                getSupportFragmentManager(),
                FRAGMENTS,
                TITLES
        );

        // attach to viewpager
        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(sectionsPageAdapter);

        tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
    }

    private class SectionsPageAdapter extends FragmentStatePagerAdapter {

        private final Fragment[] fragments;
        private final String[] fragmentTitles;

        public SectionsPageAdapter(@NonNull FragmentManager fm, Fragment[] fragments, String[] fragmentTitles) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

            this.fragments = fragments;
            this.fragmentTitles = fragmentTitles;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitles[position];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        mAuth = FirebaseAuth.getInstance();

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        String messagingToken = instanceIdResult.getToken();

                        db.collection("officers")
                                .document(mAuth.getCurrentUser().getUid())
                                .update("token", messagingToken)

                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e(TAG, "onFailure: ", e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "onFailure: ", e);
                    }
                });
    }
}