package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.R;
import com.example.travelpath.databinding.FragmentProfileBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;

/**
 * Fragment Profil — affiche les informations de l'utilisateur et ses statistiques.
 *
 * Corrections :
 *   - setUserName() passe par MainViewModel, qui expose une méthode dédiée.
 *     Le fragment n'accède plus directement à UserPreferencesManager.
 *   - Le Disposable RxJava du dialog est géré dans le ViewModel.
 *   - SavedRoutesViewModel scoped à l'activité pour partager les données
 *     avec SavedFragment sans double requête Room.
 */
public final class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private MainViewModel          mainViewModel;
    private SavedRoutesViewModel   savedViewModel;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Scoped à l'activité : données partagées entre onglets, survive aux rotations
        mainViewModel  = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        savedViewModel = new ViewModelProvider(requireActivity()).get(SavedRoutesViewModel.class);

        setupActions();
        observeViewModels();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private void setupActions() {
        binding.btnEditProfile.setOnClickListener(v -> showEditNameDialog());
        setupLanguageToggle();
    }

    private void setupLanguageToggle() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        boolean isEn = !locales.isEmpty() && "en".equals(locales.get(0).getLanguage());
        binding.toggleLanguage.check(isEn ? R.id.btnLangEn : R.id.btnLangFr);

        binding.toggleLanguage.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String tag = checkedId == R.id.btnLangEn ? "en" : "fr";
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
        });
    }

    // =========================================================================
    // Observations
    // =========================================================================

    private void observeViewModels() {
        mainViewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name != null && !name.isEmpty()) {
                binding.tvUserName.setText(name);
            }
        });

        savedViewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            int count = itineraries != null ? itineraries.size() : 0;
            binding.tvSavedCount.setText(String.valueOf(count));
        });
    }

    // =========================================================================
    // Dialog édition du nom
    // =========================================================================

    private void showEditNameDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.hint_enter_name);

        // Pré-remplir avec le nom actuel
        String current = mainViewModel.getUserName().getValue();
        if (current != null) input.setText(current);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_profile_title)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        // Passe par le ViewModel — pas de RxJava dans le fragment
                        mainViewModel.setUserName(newName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
