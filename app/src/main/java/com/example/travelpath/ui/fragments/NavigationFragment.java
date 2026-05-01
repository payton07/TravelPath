package com.example.travelpath.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.databinding.FragmentNavigationBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.maps.android.PolyUtil;
import java.util.ArrayList;
import java.util.List;

public final class NavigationFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_ITINERARY = "itinerary";

    private FragmentNavigationBinding binding;
    private GoogleMap                 googleMap;
    private final Gson                gson = new Gson();
    private List<PointOfInterest>     pois = new ArrayList<>();
    private int                       currentStep   = 0;
    private double                    totalBudget   = 0;

    private final ActivityResultLauncher<String> locationPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted && googleMap != null) enableMyLocation();
        });

    // ── Factory ───────────────────────────────────────────────────────────────

    public static NavigationFragment newInstance(@NonNull Itinerary itinerary) {
        NavigationFragment f = new NavigationFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ITINERARY, itinerary);
        f.setArguments(args);
        return f;
    }

    // ── Cycle de vie Fragment ─────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNavigationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Itinerary it = extractItinerary();
        if (it == null) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        pois        = parseSteps(it.getFullStepsJson());
        totalBudget = it.getCost();

        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());

        binding.btnNavPrev.setOnClickListener(v -> goToStep(currentStep - 1));

        binding.btnNavNext.setOnClickListener(v -> {
            if (currentStep < pois.size() - 1) {
                goToStep(currentStep + 1);
            } else {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);
    }

    @Override public void onStart()      { super.onStart();     safeMap(m -> m.onStart()); }
    @Override public void onResume()     { super.onResume();    safeMap(m -> m.onResume()); }
    @Override public void onPause()      { safeMap(m -> m.onPause());  super.onPause(); }
    @Override public void onStop()       { safeMap(m -> m.onStop());   super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); safeMap(m -> m.onLowMemory()); }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        safeMap(m -> m.onSaveInstanceState(outState));
    }

    @Override
    public void onDestroyView() {
        if (binding != null) binding.mapView.onDestroy();
        super.onDestroyView();
        binding = null;
    }

    // ── Google Maps ───────────────────────────────────────────────────────────

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);

        Itinerary it = extractItinerary();
        if (it != null) displayRoute(it);

        requestLocationAndEnable();

        if (!pois.isEmpty()) goToStep(0);
    }

    private void displayRoute(@NonNull Itinerary it) {
        if (it.getEncodedPolyline() != null && !it.getEncodedPolyline().isEmpty()) {
            googleMap.addPolyline(new PolylineOptions()
                    .addAll(PolyUtil.decode(it.getEncodedPolyline()))
                    .width(14f)
                    .color(ContextCompat.getColor(requireContext(), R.color.route_blue))
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .jointType(JointType.ROUND)
                    .geodesic(true));
        }

        if (pois.isEmpty()) return;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (int i = 0; i < pois.size(); i++) {
            PointOfInterest poi = pois.get(i);
            LatLng pos = new LatLng(poi.getLatitude(), poi.getLongitude());
            googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title((i + 1) + ". " + poi.getName()));
            bounds.include(pos);
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80));
    }

    // ── Navigation steps ──────────────────────────────────────────────────────

    private void goToStep(int index) {
        if (index < 0 || index >= pois.size()) return;
        currentStep = index;
        updateStepPanel();

        PointOfInterest poi = pois.get(currentStep);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(poi.getLatitude(), poi.getLongitude()), 16f));
    }

    private void updateStepPanel() {
        if (binding == null) return;
        PointOfInterest poi = pois.get(currentStep);

        binding.tvNavStep.setText(getString(R.string.nav_step_counter, currentStep + 1, pois.size()));
        binding.tvNavPoiName.setText(poi.getName());
        binding.tvNavPoiCategory.setText(poi.getCategory() != null
                ? poi.getCategory().toUpperCase() : "");

        PointOfInterest.OpeningHours hours = poi.getOpeningHours();
        if (hours != null) {
            binding.tvNavOpenStatus.setVisibility(View.VISIBLE);
            boolean open = hours.isOpenNow();
            binding.tvNavOpenStatus.setText(open ? R.string.open : R.string.closed);
            binding.tvNavOpenStatus.setTextColor(ContextCompat.getColor(requireContext(),
                    open ? R.color.emerald_primary : android.R.color.holo_red_dark));
        } else {
            binding.tvNavOpenStatus.setVisibility(View.GONE);
        }

        boolean isFirst = currentStep == 0;
        boolean isLast  = currentStep == pois.size() - 1;
        binding.btnNavPrev.setEnabled(!isFirst);
        binding.btnNavPrev.setAlpha(isFirst ? 0.4f : 1f);
        binding.btnNavNext.setText(isLast ? R.string.nav_finish : R.string.nav_next);

        updateBudgetRow();
    }

    private void updateBudgetRow() {
        // Sum costs of steps already visited (indices 0..currentStep-1)
        double spent = 0;
        for (int i = 0; i < currentStep; i++) spent += stepCost(i);
        double remaining = Math.max(0, totalBudget - spent);

        binding.tvNavBudget.setText(
                getString(R.string.nav_budget_remaining, remaining));
        binding.tvNavBudgetTotal.setText(
                getString(R.string.nav_budget_total, totalBudget));

        int progressPct = totalBudget > 0 ? (int) (remaining / totalBudget * 100) : 100;
        binding.progressBudget.setProgress(progressPct);

        // Turn bar red when less than 20 % remains
        int tintColor = ContextCompat.getColor(requireContext(),
                progressPct < 20 ? android.R.color.holo_red_dark : R.color.color_accent);
        binding.progressBudget.setProgressTintList(ColorStateList.valueOf(tintColor));
    }

    /** Cost of step i: use baseCost if > 0, else spread total evenly across all stops. */
    private double stepCost(int i) {
        if (i < 0 || i >= pois.size()) return 0;
        double base = pois.get(i).getBaseCost();
        if (base > 0) return base;
        return pois.isEmpty() ? 0 : totalBudget / pois.size();
    }

    // ── Localisation ──────────────────────────────────────────────────────────

    private void requestLocationAndEnable() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void enableMyLocation() {
        try {
            googleMap.setMyLocationEnabled(true);
        } catch (SecurityException ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @NonNull
    private List<PointOfInterest> parseSteps(@Nullable String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            List<PointOfInterest> result = gson.fromJson(json,
                    new TypeToken<List<PointOfInterest>>(){}.getType());
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Nullable
    private Itinerary extractItinerary() {
        if (getArguments() == null) return null;
        return (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
    }

    private void safeMap(MapViewAction action) {
        if (binding != null) action.run(binding.mapView);
    }
    private interface MapViewAction { void run(com.google.android.gms.maps.MapView v); }
}
