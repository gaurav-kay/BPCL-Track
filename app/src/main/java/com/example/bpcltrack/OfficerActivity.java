package com.example.bpcltrack;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OfficerActivity extends AppCompatActivity {

    private SectionsPageAdapter sectionsPageAdapter;

    private ViewPager viewPager;
    private TabLayout tabLayout;

    private final Fragment[] FRAGMENTS = new Fragment[]{new ReportsFragment(), new CurrentLocationsFragment(), new DeviationReportsFragment()};
    private final String[] TITLES = new String[]{"Reports", "Current Locations", "Deviation Reports"};

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
}