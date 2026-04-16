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
import com.example.travelpath.R;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentExploreBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;

public class ExploreFragment extends Fragment {

    private FragmentExploreBinding binding;
    private MainViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentExploreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        setupDestinationAutocomplete();
        setupMandatoryPois();
        setupSliders();
        setupInterests();
        setupEffortToggle();
        setupWeatherSelection();
        setupActions();
        observeViewModel();
    }

    private void setupDestinationAutocomplete() {
        // Pour la démo, on utilise une liste simple. 
        // À connecter au PlacesClient pour une autocomplétion réelle.
        String[] cities = {"Paris, France", "London, UK", "Rome, Italy", "New York, USA", "Tokyo, Japan"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_dropdown_item_1line, cities);
        binding.autoCompleteDest.setAdapter(adapter);
        
        binding.autoCompleteDest.setOnItemClickListener((parent, view, position, id) -> {
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
            List<Float> values = binding.sliderBudgetRange.getValues();
            if (values.size() >= 2) {
                binding.tvBudgetRange.setText(String.format("€%.0f - €%.0f", values.get(0), values.get(1)));
                viewModel.setBudgetRange(values.get(0).intValue(), values.get(1).intValue());
            }
        });

        binding.sliderDurationRange.addOnChangeListener((slider, value, fromUser) -> {
            List<Float> values = binding.sliderDurationRange.getValues();
            if (values.size() >= 2) {
                binding.tvDurationRange.setText(String.format("%.0fh - %.0fh", values.get(0), values.get(1)));
                viewModel.setDurationRange(values.get(0).intValue(), values.get(1).intValue());
            }
        });
    }

    private void setupInterests() {
        for (int i = 0; i < binding.chipGroupInterests.getChildCount(); i++) {
            Chip chip = (Chip) binding.chipGroupInterests.getChildAt(i);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                viewModel.toggleInterest(chip.getText().toString());
            });
        }
    }

    private void setupEffortToggle() {
        binding.toggleEffort.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnEasy) viewModel.setEffortLevel(SearchCriteria.EFFORT_EASY);
                else if (checkedId == R.id.btnModerate) viewModel.setEffortLevel(SearchCriteria.EFFORT_MODERATE);
                else if (checkedId == R.id.btnHigh) viewModel.setEffortLevel(SearchCriteria.EFFORT_HIGH);
            }
        });
    }

    private void setupWeatherSelection() {
        binding.cardSnow.setOnClickListener(v -> viewModel.toggleWeatherPreference("SNOW"));
        binding.cardRain.setOnClickListener(v -> viewModel.toggleWeatherPreference("RAIN"));
        binding.cardSun.setOnClickListener(v -> viewModel.toggleWeatherPreference("SUN"));
    }

    private void setupActions() {
        binding.btnRegenerate.setOnClickListener(v -> {
            SearchCriteria criteria = new SearchCriteria()
                    .destination(viewModel.getDestinationCity().getValue(), viewModel.getDestinationPlaceId().getValue())
                    .mandatoryPois(viewModel.getMandatoryPois().getValue())
                    .budget(viewModel.getBudgetMin().getValue(), viewModel.getBudgetMax().getValue())
                    .duration(viewModel.getDurationMin().getValue(), viewModel.getDurationMax().getValue())
                    .interests(viewModel.getSelectedInterests().getValue())
                    .effort(viewModel.getEffortLevel().getValue())
                    .weather(viewModel.getWeatherPreferences().getValue());

            if (criteria.getInterests().isEmpty()) {
                Toast.makeText(getContext(), "Please select at least one interest", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(getContext(), getString(R.string.generating_toast), Toast.LENGTH_SHORT).show();
            
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, RoutesFragment.newInstance(criteria))
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void observeViewModel() {
        viewModel.getWeatherPreferences().observe(getViewLifecycleOwner(), this::updateWeatherUI);
        
        viewModel.getMandatoryPois().observe(getViewLifecycleOwner(), this::updateMandatoryChips);

        viewModel.getDestinationCity().observe(getViewLifecycleOwner(), city -> {
            if (!city.equals(binding.autoCompleteDest.getText().toString())) {
                binding.autoCompleteDest.setText(city, false);
            }
        });
    }

    private void updateMandatoryChips(List<String> pois) {
        binding.chipGroupMandatory.removeAllViews();
        for (String poi : pois) {
            Chip chip = new Chip(requireContext());
            chip.setText(poi);
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> viewModel.removeMandatoryPoi(poi));
            binding.chipGroupMandatory.addView(chip);
        }
    }

    private void updateWeatherUI(List<String> selectedWeathers) {
        resetWeatherCard(binding.cardSnow, binding.icSnow, binding.tvSnow);
        resetWeatherCard(binding.cardRain, binding.icRain, binding.tvRain);
        resetWeatherCard(binding.cardSun, binding.icSun, binding.tvSun);

        for (String weather : selectedWeathers) {
            if ("SNOW".equals(weather)) highlightWeatherCard(binding.cardSnow, binding.icSnow, binding.tvSnow);
            else if ("RAIN".equals(weather)) highlightWeatherCard(binding.cardRain, binding.icRain, binding.tvRain);
            else if ("SUN".equals(weather)) highlightWeatherCard(binding.cardSun, binding.icSun, binding.tvSun);
        }
    }

    private void resetWeatherCard(com.google.android.material.card.MaterialCardView card, android.widget.ImageView icon, android.widget.TextView text) {
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_bg));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_secondary)));
        text.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted));
    }

    private void highlightWeatherCard(com.google.android.material.card.MaterialCardView card, android.widget.ImageView icon, android.widget.TextView text) {
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.emerald_primary));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white)));
        text.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
