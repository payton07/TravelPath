package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentExploreBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import timber.log.Timber;
import java.util.List;

/**
 * Fragment Explore — saisie des préférences utilisateur.
 *
 * Ce fragment ne contient que de la logique d'affichage :
 *   - Il reçoit des événements UI (clics, sliders, chips) et les délègue au ViewModel.
 *   - Il observe le ViewModel et met à jour les vues en conséquence.
 *   - La navigation est déléguée à {@link MainActivity#navigateTo}.
 *   - La construction de {@link SearchCriteria} est entièrement dans le ViewModel.
 */
public final class ExploreFragment extends Fragment {

    private FragmentExploreBinding binding;
    private MainViewModel viewModel;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Scoped à l'activité : le ViewModel survit aux rotations et est partagé avec ProfileFragment
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupDestinationAutocomplete();
        setupMandatoryPois();
        setupSliders();
        setupInterests();
        setupEffortToggle();
        setupWeatherSelection();
        setupGenerateButton();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;  // évite les fuites mémoire avec ViewBinding
    }

    // =========================================================================
    // Setup des contrôles UI
    // =========================================================================

    private void setupDestinationAutocomplete() {
        // Liste statique pour la démo — à remplacer par PlacesClient.findAutocompletePredictions()
        String[] cities = {"Paris, France", "London, UK", "Rome, Italy", "New York, USA", "Tokyo, Japan"};
        binding.autoCompleteDest.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, cities));

        binding.autoCompleteDest.setOnItemClickListener((parent, v, position, id) -> {
            String selection = (String) parent.getItemAtPosition(position);
            viewModel.setDestination(selection, "mock_place_id_" + selection);
        });
    }

    private void setupMandatoryPois() {
        binding.btnAddMandatory.setOnClickListener(v -> {
            String poi = binding.etMandatory.getText().toString().trim();
            if (!poi.isEmpty()) {
                viewModel.addMandatoryPoi(poi);
                binding.etMandatory.setText("");
            }
        });
    }

    private void setupSliders() {
        binding.sliderBudgetRange.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            if (values.size() >= 2) {
                binding.tvBudgetRange.setText(
                    String.format("€%.0f - €%.0f", values.get(0), values.get(1)));
                viewModel.setBudgetRange(values.get(0).intValue(), values.get(1).intValue());
            }
        });

        binding.sliderDurationRange.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = slider.getValues();
            if (values.size() >= 2) {
                binding.tvDurationRange.setText(
                    String.format("%.0fh - %.0fh", values.get(0), values.get(1)));
                viewModel.setDurationRange(values.get(0).intValue(), values.get(1).intValue());
            }
        });
    }

    private void setupInterests() {
        for (int i = 0; i < binding.chipGroupInterests.getChildCount(); i++) {
            Chip chip = (Chip) binding.chipGroupInterests.getChildAt(i);
            chip.setOnCheckedChangeListener((btn, isChecked) ->
                viewModel.toggleInterest(chip.getText().toString()));
        }
    }

    private void setupEffortToggle() {
        binding.toggleEffort.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if      (checkedId == R.id.btnEasy)     viewModel.setEffortLevel(SearchCriteria.EFFORT_EASY);
            else if (checkedId == R.id.btnModerate) viewModel.setEffortLevel(SearchCriteria.EFFORT_MODERATE);
            else if (checkedId == R.id.btnHigh)     viewModel.setEffortLevel(SearchCriteria.EFFORT_HIGH);
        });
    }

    private void setupWeatherSelection() {
        binding.cardSnow.setOnClickListener(v -> viewModel.toggleWeatherPreference("SNOW"));
        binding.cardRain.setOnClickListener(v -> viewModel.toggleWeatherPreference("RAIN"));
        binding.cardSun.setOnClickListener(v  -> viewModel.toggleWeatherPreference("SUN"));
    }

    /**
     * Bouton "Générer" : valide les entrées, délègue la construction des critères
     * au ViewModel, puis navigue vers RoutesFragment via MainActivity.
     */
    private void setupGenerateButton() {
        binding.btnRegenerate.setOnClickListener(v -> {
            String cityInput = binding.autoCompleteDest.getText().toString().trim();
            if (cityInput.isEmpty()) {
                Toast.makeText(getContext(), R.string.error_enter_destination, Toast.LENGTH_SHORT).show();
                return;
            }

            // Synchroniser la ville si l'utilisateur l'a tapée manuellement
            String currentCity = viewModel.getDestinationCity().getValue();
            if (!cityInput.equals(currentCity)) {
                viewModel.setDestination(cityInput, null);
            }

            // Déléguer la validation et la construction des critères au ViewModel
            SearchCriteria criteria = viewModel.buildCriteria();
            if (criteria == null) {
                Toast.makeText(getContext(),
                    R.string.error_select_interest, Toast.LENGTH_SHORT).show();
                return;
            }

            Timber.d("Navigation vers RoutesFragment — ville : %s", cityInput);
            ((MainActivity) requireActivity()).navigateTo(
                RoutesFragment.newInstance(criteria), "routes");
        });
    }

    // =========================================================================
    // Observations
    // =========================================================================

    private void observeViewModel() {
        viewModel.getWeatherPreferences().observe(getViewLifecycleOwner(),
            this::updateWeatherUI);

        viewModel.getMandatoryPois().observe(getViewLifecycleOwner(),
            this::updateMandatoryChips);

        viewModel.getDestinationCity().observe(getViewLifecycleOwner(), city -> {
            if (city != null && !city.equals(binding.autoCompleteDest.getText().toString())) {
                binding.autoCompleteDest.setText(city, false);
            }
        });
    }

    // =========================================================================
    // Helpers d'affichage
    // =========================================================================

    private void updateMandatoryChips(List<String> pois) {
        binding.chipGroupMandatory.removeAllViews();
        if (pois == null) return;
        for (String poi : pois) {
            Chip chip = new Chip(requireContext());
            chip.setText(poi);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> viewModel.removeMandatoryPoi(poi));
            binding.chipGroupMandatory.addView(chip);
        }
    }

    private void updateWeatherUI(List<String> selected) {
        if (selected == null) return;
        applyWeatherCard(binding.cardSnow, "SNOW",  selected);
        applyWeatherCard(binding.cardRain, "RAIN",  selected);
        applyWeatherCard(binding.cardSun,  "SUN",   selected);
    }

    private void applyWeatherCard(MaterialCardView card, String key, List<String> selected) {
        boolean active = selected.contains(key);
        int bgColor   = active ? R.color.emerald_primary : R.color.card_bg;
        int iconColor = active ? android.R.color.white   : R.color.text_secondary;
        int textColor = active ? android.R.color.white   : R.color.text_muted;

        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), bgColor));

        // Les icônes et textes sont toujours le 1er et 2ème enfant du card
        if (card.getChildCount() >= 1) {
            View child = card.getChildAt(0);
            if (child instanceof android.widget.LinearLayout ll && ll.getChildCount() >= 2) {
                if (ll.getChildAt(0) instanceof android.widget.ImageView iv) {
                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), iconColor)));
                }
                if (ll.getChildAt(1) instanceof android.widget.TextView tv) {
                    tv.setTextColor(ContextCompat.getColor(requireContext(), textColor));
                }
            }
        }
    }
}
