package com.example.travelpath;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.example.travelpath.databinding.ActivityMainBinding;
import com.example.travelpath.ui.fragments.ExploreFragment;
import com.example.travelpath.ui.fragments.ProfileFragment;
import com.example.travelpath.ui.fragments.SavedFragment;

/**
 * Activité hôte unique — héberge la navigation par onglets (Bottom Navigation).
 *
 * Stratégie de navigation :
 *   - Chaque onglet possède son propre back-stack (tag unique).
 *   - show/hide au lieu de replace() → les fragments gardent leur état de scroll.
 *   - Le bouton Back système ferme l'app uniquement depuis l'onglet Explore.
 */
public final class MainActivity extends AppCompatActivity {

    // Tags des fragments — constants pour éviter les typos
    private static final String TAG_EXPLORE = "explore";
    private static final String TAG_SAVED   = "saved";
    private static final String TAG_PROFILE = "profile";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();

        if (savedInstanceState == null) {
            showFragment(TAG_EXPLORE);
            binding.bottomNavigation.setSelectedItemId(R.id.nav_explore);
        }
    }

    private void setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_explore) showFragment(TAG_EXPLORE);
            else if (id == R.id.nav_saved)   showFragment(TAG_SAVED);
            else if (id == R.id.nav_profile) showFragment(TAG_PROFILE);
            else return false;
            return true;
        });
    }

    /**
     * Affiche le fragment correspondant au tag, en le créant si besoin.
     * Les fragments déjà créés sont cachés/montrés — leur état est préservé.
     */
    private void showFragment(@NonNull String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tag);

        androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
        
        // Cacher tous les fragments actifs
        for (Fragment f : fm.getFragments()) {
            if (f != null && f.isVisible()) tx.hide(f);
        }
        
        // Ajouter ou afficher le fragment cible
        if (target == null) {
            tx.add(R.id.fragment_container, createFragment(tag), tag);
        } else {
            tx.show(target);
        }
        
        tx.commit();
    }

    @NonNull
    private Fragment createFragment(@NonNull String tag) {
        switch (tag) {
            case TAG_SAVED:   return new SavedFragment();
            case TAG_PROFILE: return new ProfileFragment();
            default:          return new ExploreFragment();
        }
    }

    @Override
    public void onBackPressed() {
        // Si on n'est pas sur Explore, revenir à Explore
        Fragment current = getSupportFragmentManager().findFragmentByTag(TAG_EXPLORE);
        if (current == null || !current.isVisible()) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_explore);
        } else {
            super.onBackPressed();
        }
    }
}
