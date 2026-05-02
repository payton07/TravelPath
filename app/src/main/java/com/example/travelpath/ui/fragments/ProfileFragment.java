package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.FragmentProfileBinding;
import com.example.travelpath.databinding.LayoutInputDialogBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;
import com.example.travelpath.ui.widget.MessageBanner;
import com.google.android.material.chip.Chip;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private MainViewModel          mainViewModel;
    private SavedRoutesViewModel   savedViewModel;

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
        binding.rowEditName.setOnClickListener(v -> showEditNameDialog());
        binding.rowClearCache.setOnClickListener(v -> mainViewModel.clearCache());
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
                binding.tvAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
            }
        });

        mainViewModel.getSelectedInterests().observe(getViewLifecycleOwner(), this::renderInterestChips);

        savedViewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            int count = itineraries != null ? itineraries.size() : 0;
            binding.tvStatTrips.setText(String.valueOf(count));
            binding.tvStatCities.setText(String.valueOf(countDistinctCities(itineraries)));
        });

        mainViewModel.getCacheClearedEvent().observe(getViewLifecycleOwner(), cleared -> {
            if (cleared == null || !cleared) return;
            ((MainActivity) requireActivity()).showMessage(
                MessageBanner.Type.SUCCESS,
                getString(R.string.profile_clear_cache_success));
        });
    }

    // =========================================================================
    // Interest chips
    // =========================================================================

    private void renderInterestChips(@Nullable List<String> interests) {
        binding.interestChipsContainer.removeAllViews();
        if (interests == null || interests.isEmpty()) {
            binding.tvStyleEmpty.setVisibility(View.VISIBLE);
            return;
        }
        binding.tvStyleEmpty.setVisibility(View.GONE);
        for (String interest : interests) {
            Chip chip = new Chip(requireContext());
            chip.setText(interest);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.color_butter);
            chip.setTextColor(requireContext().getColor(R.color.color_ink));
            binding.interestChipsContainer.addView(chip);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int countDistinctCities(@Nullable List<Itinerary> itineraries) {
        if (itineraries == null || itineraries.isEmpty()) return 0;
        Set<String> cities = new HashSet<>();
        for (Itinerary it : itineraries) {
            if (it.getDestinationCity() != null) cities.add(it.getDestinationCity());
        }
        return cities.size();
    }

    // =========================================================================
    // Edit name dialog
    // =========================================================================

    private void showEditNameDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        LayoutInputDialogBinding d = LayoutInputDialogBinding.inflate(getLayoutInflater());
        sheet.setContentView(d.getRoot());

        d.tvDialogTitle.setText(R.string.edit_profile_title);
        d.etDialogInput.setHint(R.string.hint_enter_name);

        String current = mainViewModel.getUserName().getValue();
        if (current != null) d.etDialogInput.setText(current);

        d.btnDialogCancel.setOnClickListener(v -> sheet.dismiss());
        d.btnDialogConfirm.setOnClickListener(v -> {
            String newName = d.etDialogInput.getText().toString().trim();
            if (!newName.isEmpty()) {
                mainViewModel.setUserName(newName);
                sheet.dismiss();
            }
        });

        sheet.show();
        d.etDialogInput.requestFocus();
        InputMethodManager imm = (InputMethodManager)
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(d.etDialogInput, InputMethodManager.SHOW_IMPLICIT);
    }
}
