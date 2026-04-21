package com.example.travelpath;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.example.travelpath.databinding.ActivityMainBinding;
import com.example.travelpath.ui.fragments.ExploreFragment;
import com.example.travelpath.ui.fragments.ProfileFragment;
import com.example.travelpath.ui.fragments.SavedFragment;
import timber.log.Timber;

/**
 * Activité hôte unique — Single-Activity Architecture.
 *
 * <h2>Stratégie de navigation</h2>
 * Les 3 onglets principaux (Explore / Saved / Profile) utilisent la stratégie
 * <b>show/hide</b> : les fragments sont créés une seule fois et leur état de scroll
 * est préservé entre les changements d'onglet.
 *
 * Les fragments de détail (RoutesFragment, RouteDetailFragment) sont empilés via
 * {@code addToBackStack()} depuis les fragments onglet — ils forment le back-stack
 * géré par le {@link FragmentManager}.
 *
 * <h2>Gestion du bouton Back</h2>
 * <ol>
 *   <li>Back-stack non vide → dépiler le fragment de détail.</li>
 *   <li>Back-stack vide, onglet actif ≠ Explore → revenir à Explore.</li>
 *   <li>Back-stack vide, onglet actif = Explore → quitter l'app.</li>
 * </ol>
 */
public final class MainActivity extends AppCompatActivity {

    private static final String TAG_EXPLORE = "explore";
    private static final String TAG_SAVED   = "saved";
    private static final String TAG_PROFILE = "profile";

    /** Tag de l'onglet affiché avant de pousser un fragment de détail. */
    private static final String KEY_ACTIVE_TAB = "active_tab";

    private ActivityMainBinding binding;
    private String activeTabTag = TAG_EXPLORE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            // Restaurer l'onglet actif après rotation / kill processus
            activeTabTag = savedInstanceState.getString(KEY_ACTIVE_TAB, TAG_EXPLORE);
        }

        setupNavigation();
        setupBackPress();

        if (savedInstanceState == null) {
            showTab(TAG_EXPLORE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACTIVE_TAB, activeTabTag);
    }

    @Override
    protected void onDestroy() {
        // Éviter les fuites mémoire sur ViewBinding avec une activité
        binding = null;
        super.onDestroy();
    }

    // =========================================================================
    // Navigation onglets
    // =========================================================================

    private void setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            String tag;
            if      (id == R.id.nav_explore) tag = TAG_EXPLORE;
            else if (id == R.id.nav_saved)   tag = TAG_SAVED;
            else if (id == R.id.nav_profile) tag = TAG_PROFILE;
            else return false;

            // Vider le back-stack (fragments de détail) avant de changer d'onglet
            clearDetailBackStack();
            showTab(tag);
            return true;
        });

        // Clic sur l'onglet déjà actif : remonter en haut / vider le back-stack
        binding.bottomNavigation.setOnItemReselectedListener(item -> clearDetailBackStack());
    }

    /**
     * Affiche l'onglet correspondant au tag, en le créant si nécessaire.
     * Utilise show/hide pour préserver l'état des fragments existants.
     */
    private void showTab(@NonNull String tag) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment target = fm.findFragmentByTag(tag);

        // Ne s'arrêter que si l'onglet est actif ET que le fragment est déjà affiché
        if (tag.equals(activeTabTag) && target != null && target.isVisible() && fm.getBackStackEntryCount() == 0) {
            return;
        }

        FragmentTransaction tx = fm.beginTransaction();

        // Masquer tous les fragments onglet visibles
        for (Fragment f : fm.getFragments()) {
            if (f != null && f.isAdded() && f.isVisible()) {
                tx.hide(f);
            }
        }

        if (target == null) {
            tx.add(R.id.fragment_container, createTabFragment(tag), tag);
            Timber.d("Fragment créé : %s", tag);
        } else {
            tx.show(target);
        }

        tx.commit();
    }

    @NonNull
    private Fragment createTabFragment(@NonNull String tag) {
        switch (tag) {
            case TAG_SAVED:   return new SavedFragment();
            case TAG_PROFILE: return new ProfileFragment();
            default:          return new ExploreFragment();
        }
    }

    /**
     * Dépile tous les fragments de détail de manière synchrone.
     * {@code popBackStackImmediate} est utilisé intentionnellement ici :
     * on veut que le back-stack soit vide AVANT d'afficher l'onglet,
     * pour éviter un flash visuel du fragment de détail.
     */
    private void clearDetailBackStack() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    // =========================================================================
    // Gestion du bouton Back
    // =========================================================================

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();

                // Cas 1 : fragment de détail empilé → dépiler
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return;
                }

                // Cas 2 : onglet actif ≠ Explore → revenir à Explore
                if (!TAG_EXPLORE.equals(activeTabTag)) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_explore);
                    return;
                }

                // Cas 3 : déjà sur Explore → quitter l'app
                // Désactiver le callback le temps de déléguer au système,
                // puis le réactiver pour les prochains appuis.
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    // =========================================================================
    // API publique pour les fragments enfants
    // =========================================================================

    /**
     * Permet aux fragments onglet (Explore, Saved) de pousser un fragment de
     * détail dans le back-stack sans connaître le container ID.
     *
     * Usage depuis ExploreFragment :
     * <pre>
     *   ((MainActivity) requireActivity()).navigateTo(new RoutesFragment(), "routes");
     * </pre>
     */
    public void navigateTo(@NonNull Fragment fragment, @NonNull String backStackName) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .replace(R.id.fragment_container, fragment, backStackName)
                .addToBackStack(backStackName)
                .commit();
    }
}
