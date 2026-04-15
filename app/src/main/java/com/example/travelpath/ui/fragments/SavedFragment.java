package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.travelpath.databinding.FragmentSavedBinding;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;

public class SavedFragment extends Fragment {

    private FragmentSavedBinding binding;
    private SavedRoutesViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SavedRoutesViewModel.class);

        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        binding.rvSavedRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void observeViewModel() {
        viewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries == null || itineraries.isEmpty()) {
                binding.tvEmptyMessage.setVisibility(View.VISIBLE);
                binding.rvSavedRoutes.setVisibility(View.GONE);
            } else {
                binding.tvEmptyMessage.setVisibility(View.GONE);
                binding.rvSavedRoutes.setVisibility(View.VISIBLE);
                // Mettre à jour l'adaptateur
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
