package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import com.example.travelpath.R;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentRoutesBinding;
import com.example.travelpath.ui.viewmodels.RouteViewModel;
import java.util.List;

public class RoutesFragment extends Fragment {

    private static final String ARG_CRITERIA = "search_criteria";
    private FragmentRoutesBinding binding;
    private RouteViewModel viewModel;
    private RouteAdapter adapter;

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

        setupViewPager();
        observeViewModel();

        if (getArguments() != null) {
            SearchCriteria criteria = (SearchCriteria) getArguments().getSerializable(ARG_CRITERIA);
            if (criteria != null) {
                viewModel.generateRoutes(criteria);
            }
        }
    }

    private void setupViewPager() {
        adapter = new RouteAdapter(true, new RouteAdapter.OnRouteClickListener() {
            @Override
            public void onRouteClick(Itinerary itinerary) {
                openDetail(itinerary);
            }

            @Override
            public void onLikeClick(Itinerary itinerary) {
                itinerary.setSaved(!itinerary.isSaved());
                ((TravelApplication) requireActivity().getApplication()).getRepository().update(itinerary)
                        .subscribe(() -> {}, throwable -> {
                            android.util.Log.e("RoutesFragment", "Erreur lors de la sauvegarde", throwable);
                        });
            }
        });

        binding.viewPagerRoutes.setAdapter(adapter);
        
        // Settings pour laisser apercevoir les cartes suivantes/précédentes
        binding.viewPagerRoutes.setOffscreenPageLimit(3);
        binding.viewPagerRoutes.setClipToPadding(false);
        binding.viewPagerRoutes.setClipChildren(false);

        // Ajout d'un effet de transformation au glissement (Zoom Out subtil)
        binding.viewPagerRoutes.setPageTransformer((page, position) -> {
            float MIN_SCALE = 0.9f;
            float MIN_ALPHA = 0.6f;
            
            float scaleFactor = Math.max(MIN_SCALE, 1 - Math.abs(position));
            page.setScaleX(scaleFactor);
            page.setScaleY(scaleFactor);
            
            page.setAlpha(MIN_ALPHA + (scaleFactor - MIN_SCALE) / (1 - MIN_SCALE) * (1 - MIN_ALPHA));
        });
    }

    private void observeViewModel() {
        viewModel.getIsGenerating().observe(getViewLifecycleOwner(), isGenerating -> {
            binding.progressBar.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
            if (isGenerating) {
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
                binding.tvEmptyRoutes.setText("Génération de vos parcours...");
                binding.viewPagerRoutes.setVisibility(View.GONE);
            }
        });

        viewModel.getRoutes().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries != null && !itineraries.isEmpty()) {
                binding.tvEmptyRoutes.setVisibility(View.GONE);
                binding.viewPagerRoutes.setVisibility(View.VISIBLE);
                adapter.setItineraries(itineraries);
            } else if (Boolean.FALSE.equals(viewModel.getIsGenerating().getValue())) {
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
                binding.tvEmptyRoutes.setText("Aucun parcours trouvé pour ces critères.");
                binding.viewPagerRoutes.setVisibility(View.GONE);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                binding.tvEmptyRoutes.setText(error);
                binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
                binding.viewPagerRoutes.setVisibility(View.GONE);
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });
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
