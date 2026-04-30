package com.example.travelpath.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.databinding.FragmentRoutesBinding;
import com.example.travelpath.ui.adapter.RouteAdapter;
import com.example.travelpath.ui.viewmodels.RouteViewModel;
import com.example.travelpath.ui.viewmodels.UiState;
import java.util.List;

public final class RoutesFragment extends Fragment {

    private static final String ARG_CRITERIA = "search_criteria";

    private FragmentRoutesBinding binding;
    private RouteViewModel        viewModel;
    private RouteAdapter          adapter;

    private int totalRoutes = 0;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static RoutesFragment newInstance(@NonNull SearchCriteria criteria) {
        RoutesFragment f = new RoutesFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CRITERIA, criteria);
        f.setArguments(args);
        return f;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());

        setupViewPager();
        observeViewModel();
        triggerGenerationIfNeeded();
    }

    @Override
    public void onDestroyView() {
        binding.viewPagerRoutes.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }

    // ── ViewPager2 ────────────────────────────────────────────────────────────

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

        binding.viewPagerRoutes.setPageTransformer((page, position) -> {
            final float MIN_SCALE = 0.92f;
            final float MIN_ALPHA = 0.70f;
            float scale = Math.max(MIN_SCALE, 1f - Math.abs(position) * 0.08f);
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setAlpha(MIN_ALPHA + (scale - MIN_SCALE) / (1f - MIN_SCALE) * (1f - MIN_ALPHA));
        });

        binding.viewPagerRoutes.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updatePageCounter(position, totalRoutes);
                updatePageDots(position);
            }
        });
    }

    // ── Observations ──────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if      (state instanceof UiState.Loading) renderLoading();
            else if (state instanceof UiState.Success) renderSuccess(((UiState.Success<List<Itinerary>>) state).getData());
            else if (state instanceof UiState.Empty)   renderEmpty(getString(R.string.no_routes_found));
            else if (state instanceof UiState.Error)   renderError(((UiState.Error) state).getMessage());
        });
    }

    private void renderLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.viewPagerRoutes.setVisibility(View.GONE);
        binding.pageIndicator.setVisibility(View.GONE);
        binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setText(R.string.generating_routes);
    }

    private void renderSuccess(List<Itinerary> itineraries) {
        binding.progressBar.setVisibility(View.GONE);
        binding.viewPagerRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setVisibility(View.GONE);

        totalRoutes = itineraries.size();
        adapter.submitList(itineraries);

        setupPageIndicatorDots(totalRoutes);
        updatePageCounter(0, totalRoutes);
        updatePageDots(0);

        binding.pageIndicator.setVisibility(totalRoutes > 1 ? View.VISIBLE : View.GONE);
    }

    private void renderEmpty(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.viewPagerRoutes.setVisibility(View.GONE);
        binding.pageIndicator.setVisibility(View.GONE);
        binding.tvEmptyRoutes.setVisibility(View.VISIBLE);
        binding.tvEmptyRoutes.setText(message);
    }

    private void renderError(String message) {
        renderEmpty(message);
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    // ── Page indicator helpers ────────────────────────────────────────────────

    private void setupPageIndicatorDots(int count) {
        binding.pageIndicator.removeAllViews();
        int sizePx  = dp(8);
        int marginPx = dp(5);

        for (int i = 0; i < count; i++) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.RECTANGLE);
            dot.setCornerRadius(dp(4));
            dot.setColor(getResources().getColor(R.color.color_line, null));
            dot.setSize(sizePx, sizePx);

            View v = new View(requireContext());
            v.setBackground(dot);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(sizePx, sizePx);
            lp.setMargins(marginPx, 0, marginPx, 0);
            v.setLayoutParams(lp);
            binding.pageIndicator.addView(v);
        }
    }

    private void updatePageDots(int activePosition) {
        int count = binding.pageIndicator.getChildCount();
        for (int i = 0; i < count; i++) {
            View v = binding.pageIndicator.getChildAt(i);
            boolean active = (i == activePosition);
            int widthPx = active ? dp(20) : dp(8);
            int colorRes = active ? R.color.color_accent : R.color.color_line;

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.width = widthPx;
            v.setLayoutParams(lp);

            GradientDrawable dot = (GradientDrawable) v.getBackground();
            dot.setColor(getResources().getColor(colorRes, null));
        }
    }

    private void updatePageCounter(int position, int total) {
        if (binding == null) return;
        binding.tvPageCounter.setText((position + 1) + " / " + total);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            getResources().getDisplayMetrics()));
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void triggerGenerationIfNeeded() {
        if (getArguments() == null) return;
        SearchCriteria criteria = (SearchCriteria) getArguments().getSerializable(ARG_CRITERIA);
        if (criteria == null) return;
        if (viewModel.getUiState().getValue() instanceof UiState.Success) return;
        viewModel.generateRoutes(criteria);
    }

    private void navigateToDetail(@NonNull Itinerary itinerary) {
        ((MainActivity) requireActivity())
            .navigateTo(RouteDetailFragment.newInstance(itinerary), "detail");
    }
}
