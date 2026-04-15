package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentRoutesBinding;
import com.example.travelpath.ui.viewmodels.RouteViewModel;
import java.util.List;

public class RoutesFragment extends Fragment {

    private static final String ARG_CRITERIA = "search_criteria";
    private FragmentRoutesBinding binding;
    private RouteViewModel viewModel;

    public static RoutesFragment newInstance(SearchCriteria criteria) {
        RoutesFragment fragment = new RoutesFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRITERIA, criteria);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RouteViewModel.class);

        binding.btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        if (getArguments() != null) {
            SearchCriteria criteria = (SearchCriteria) getArguments().getSerializable(ARG_CRITERIA);
            if (criteria != null) {
                viewModel.generateRoutes(criteria);
            }
        }

        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), isGenerating -> {
            // Afficher un loader si nécessaire
        });

        viewModel.getRoutes().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries != null && !itineraries.isEmpty()) {
                updateUI(itineraries);
            }
        });
    }

    private void updateUI(List<Itinerary> itineraries) {
        // Mapping simple pour cette démo (Economy, Balanced, Comfort)
        for (Itinerary itinerary : itineraries) {
            if ("ECONOMY".equals(itinerary.getRouteType())) {
                setupCard(binding.cardRouteEconomy, itinerary);
            } else if ("BALANCED".equals(itinerary.getRouteType())) {
                setupCard(binding.cardRouteBalanced, itinerary);
            } else if ("COMFORT".equals(itinerary.getRouteType())) {
                setupCard(binding.cardRouteComfort, itinerary);
            }
        }
    }

    private void setupCard(com.google.android.material.card.MaterialCardView card, Itinerary itinerary) {
        card.setOnClickListener(v -> openDetail(itinerary));
        // On pourrait aussi mettre à jour les textes des cartes ici si on avait des IDs plus précis
    }

    private void openDetail(Itinerary itinerary) {
        RouteDetailFragment fragment = RouteDetailFragment.newInstance(itinerary);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
