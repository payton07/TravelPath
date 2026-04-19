package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentRoutesBinding;
import com.example.travelpath.databinding.ItemRouteCardBinding;
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
                binding.tvEmptyRoutes.setText("Aucun parcours trouvé pour ces critères.");
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                binding.tvEmptyRoutes.setText(error);
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateUI(List<Itinerary> itineraries) {
        for (Itinerary itinerary : itineraries) {
            String type = itinerary.getRouteType();
            if (type == null) continue;

            switch (type.toUpperCase()) {
                case "ECONOMY":
                    setupCard(ItemRouteCardBinding.bind(binding.layoutEconomy.getRoot()), itinerary, binding.cardRouteEconomy);
                    break;
                case "BALANCED":
                    setupCard(ItemRouteCardBinding.bind(binding.layoutBalanced.getRoot()), itinerary, binding.cardRouteBalanced);
                    break;
                case "COMFORT":
                    setupCard(ItemRouteCardBinding.bind(binding.layoutComfort.getRoot()), itinerary, binding.cardRouteComfort);
                    break;
            }
        }
    }

    private void setupCard(ItemRouteCardBinding cardBinding, Itinerary itinerary, View parentCard) {
        parentCard.setVisibility(View.VISIBLE);
        cardBinding.tvRouteName.setText(itinerary.getName());
        cardBinding.tvCost.setText(itinerary.getCost() + "€");
        cardBinding.tvDuration.setText(itinerary.getDuration());
        cardBinding.tvEffort.setText(itinerary.getEffort());
        cardBinding.tvWeather.setText(itinerary.getWeather());

        // Thumbnail
        if (itinerary.getImageUrl() != null && !itinerary.getImageUrl().isEmpty()) {
            Glide.with(this).load(itinerary.getImageUrl()).placeholder(R.drawable.bg_travel_mode).into(cardBinding.ivRouteThumbnail);
        }

        // Like Button
        updateLikeIcon(cardBinding, itinerary.isSaved());
        cardBinding.btnLike.setOnClickListener(v -> {
            itinerary.setSaved(!itinerary.isSaved());
            ((TravelApplication) requireActivity().getApplication()).getRepository().update(itinerary).subscribe();
            updateLikeIcon(cardBinding, itinerary.isSaved());
            
            Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.heart_pop);
            cardBinding.btnLike.startAnimation(anim);
        });

        cardBinding.btnSelectRoute.setOnClickListener(v -> openDetail(itinerary));
        parentCard.setOnClickListener(v -> openDetail(itinerary));
    }

    private void updateLikeIcon(ItemRouteCardBinding binding, boolean isLiked) {
        if (isLiked) {
            binding.btnLike.setIconResource(android.R.drawable.btn_star_big_on);
        } else {
            binding.btnLike.setIconResource(android.R.drawable.btn_star_big_off);
        }
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
