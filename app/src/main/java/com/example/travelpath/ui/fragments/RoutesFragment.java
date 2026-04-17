package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
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

    private static final String TAG = "RoutesFragment";
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

        // Reset UI
        binding.cardRouteEconomy.setVisibility(View.GONE);
        binding.cardRouteBalanced.setVisibility(View.GONE);
        binding.cardRouteComfort.setVisibility(View.GONE);
        binding.tvEmptyRoutes.setVisibility(View.GONE);

        observeViewModel();

        if (getArguments() != null) {
            SearchCriteria criteria = (SearchCriteria) getArguments().getSerializable(ARG_CRITERIA);
            if (criteria != null) {
                viewModel.generateRoutes(criteria);
            }
        }
    }

    private void observeViewModel() {
        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), isGenerating -> {
            binding.progressBar.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
        });

        viewModel.getRoutes().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries != null && !itineraries.isEmpty()) {
                binding.tvEmptyRoutes.setVisibility(View.GONE);
                updateUI(itineraries);
            } else if (Boolean.FALSE.equals(viewModel.getIsGenerating().getValue())) {
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                String message = error;
                if (error.contains("PERMISSION_DENIED")) {
                    message = "Accès refusé. Vérifiez allUsers sur la console.";
                }
                binding.tvEmptyRoutes.setText(message);
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUI(List<Itinerary> itineraries) {
        for (Itinerary itinerary : itineraries) {
            String type = itinerary.getRouteType();
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "ECONOMY":
                    fillCard(binding.cardRouteEconomy, binding.tvEconomyTitle, binding.tvEconomyDesc, itinerary);
                    break;
                case "BALANCED":
                    fillCard(binding.cardRouteBalanced, binding.tvBalancedTitle, binding.tvBalancedDesc, itinerary);
                    break;
                case "COMFORT":
                    fillCard(binding.cardRouteComfort, binding.tvComfortTitle, binding.tvComfortDesc, itinerary);
                    break;
            }
        }
    }

    private void fillCard(View card, TextView titleView, TextView descView, Itinerary itinerary) {
        card.setVisibility(View.VISIBLE);
        titleView.setText(itinerary.getName());
        descView.setText(String.format("%s • %s • %s", itinerary.getCost() + "€", itinerary.getDuration(), itinerary.getEffort()));
        card.setOnClickListener(v -> openDetail(itinerary));
    }

    private void openDetail(Itinerary itinerary) {
        RouteDetailFragment fragment = RouteDetailFragment.newInstance(itinerary);
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
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
