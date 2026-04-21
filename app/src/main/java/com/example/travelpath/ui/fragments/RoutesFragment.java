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
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentRoutesBinding;
import com.example.travelpath.ui.adapter.RouteAdapter;
import com.example.travelpath.ui.viewmodels.RouteViewModel;
import com.example.travelpath.ui.viewmodels.UiState;

/**
 * Affiche les 3 itinéraires générés dans un carrousel ViewPager2.
 *
 * Ce fragment :
 *   - Délègue la génération à {@link RouteViewModel}.
 *   - Observe un {@link UiState} unifié — pas de flags booléens séparés.
 *   - Délègue la navigation et le back-stack à {@link MainActivity}.
 *   - Ne touche jamais au Repository directement.
 */
public final class RoutesFragment extends Fragment {

    private static final String ARG_CRITERIA = "search_criteria";

    private FragmentRoutesBinding binding;
    private RouteViewModel        viewModel;
    private RouteAdapter          adapter;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static RoutesFragment newInstance(@NonNull SearchCriteria criteria) {
        RoutesFragment f = new RoutesFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRITERIA, criteria);
        f.setArguments(args);
        return f;
    }

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRoutesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RouteViewModel.class);

        // Back : délégué à MainActivity qui connaît le back-stack
        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());

        setupViewPager();
        observeViewModel();
        triggerGenerationIfNeeded();
    }

    @Override
    public void onDestroyView() {
        // Détacher l'adaptateur avant de nullifier binding — évite une fuite
        // mémoire où le RecyclerView du ViewPager2 garde une référence au pool
        binding.viewPagerRoutes.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }

    // =========================================================================
    // ViewPager2
    // =========================================================================

    private void setupViewPager() {
        adapter = new RouteAdapter(RouteAdapter.VIEW_TYPE_CAROUSEL, new RouteAdapter.OnRouteActionListener() {
            @Override
            public void onRouteClick(Itinerary itinerary) {
                navigateToDetail(itinerary);
            }

            @Override
            public void onLikeClick(Itinerary itinerary) {
                viewModel.toggleSave(itinerary);
            }
        });

        binding.viewPagerRoutes.setAdapter(adapter);
        binding.viewPagerRoutes.setOffscreenPageLimit(3);
        binding.viewPagerRoutes.setClipToPadding(false);
        binding.viewPagerRoutes.setClipChildren(false);

        // Effet Zoom-Out subtil entre les cartes
        binding.viewPagerRoutes.setPageTransformer((page, position) -> {
            final float MIN_SCALE = 0.90f;
            final float MIN_ALPHA = 0.60f;
            float scale = Math.max(MIN_SCALE, 1f - Math.abs(position));
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setAlpha(MIN_ALPHA + (scale - MIN_SCALE) / (1f - MIN_SCALE) * (1f - MIN_ALPHA));
        });
    }

    // =========================================================================
    // Observations — UiState unifié
    // =========================================================================

    private void observeViewModel() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if      (state instanceof UiState.Loading) renderLoading();
            else if (state instanceof UiState.Success) renderSuccess(((UiState.Success<java.util.List<Itinerary>>) state).getData());
            else if (state instanceof UiState.Empty)   renderEmpty(getString(R.string.no_routes_found));
            else if (state instanceof UiState.Error)   renderError(((UiState.Error) state).getMessage());
        });
    }

    private void renderLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.viewPagerRoutes.setVisibility(View.GONE);
        binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setText(R.string.generating_routes);
    }

    private void renderSuccess(java.util.List<Itinerary> itineraries) {
        binding.progressBar.setVisibility(View.GONE);
        binding.viewPagerRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setVisibility(View.GONE);
        adapter.submitList(itineraries);
    }

    private void renderEmpty(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.viewPagerRoutes.setVisibility(View.GONE);
        binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setText(message);
    }

    private void renderError(String message) {
        renderEmpty(message);
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void triggerGenerationIfNeeded() {
        if (getArguments() == null) return;
        SearchCriteria criteria = (SearchCriteria) getArguments().getSerializable(ARG_CRITERIA);
        if (criteria == null) return;

        // Ne pas regénérer si le ViewModel a déjà des données (rotation d'écran)
        if (viewModel.getUiState().getValue() instanceof UiState.Success) return;

        viewModel.generateRoutes(criteria);
    }

    private void navigateToDetail(@NonNull Itinerary itinerary) {
        ((MainActivity) requireActivity())
            .navigateTo(RouteDetailFragment.newInstance(itinerary), "detail");
    }
}
