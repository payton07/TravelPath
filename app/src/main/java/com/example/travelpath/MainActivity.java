package com.example.travelpath;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.travelpath.databinding.ActivityMainBinding;
import com.example.travelpath.ui.fragments.ExploreFragment;
import com.example.travelpath.ui.fragments.ProfileFragment;
import com.example.travelpath.ui.fragments.SavedFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();

        // Charger le fragment par défaut (Explore) au premier lancement
        if (savedInstanceState == null) {
            loadFragment(new ExploreFragment());
        }
    }

    private void setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment = null;

            if (id == R.id.nav_explore) {
                fragment = new ExploreFragment();
            } else if (id == R.id.nav_saved) {
                fragment = new SavedFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            }

            if (fragment != null) {
                loadFragment(fragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
