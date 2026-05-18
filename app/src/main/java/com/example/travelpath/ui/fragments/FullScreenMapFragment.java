package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.databinding.FragmentFullScreenMapBinding;
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

public final class FullScreenMapFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_ITINERARY = "itinerary";

    private FragmentFullScreenMapBinding binding;
    private GoogleMap                    googleMap;
    private final Gson                   gson = new Gson();

    public static FullScreenMapFragment newInstance(@NonNull Itinerary itinerary) {
        FullScreenMapFragment f = new FullScreenMapFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ITINERARY, itinerary);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFullScreenMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);
    }

    @Override public void onStart()     { super.onStart();     safeMap(m -> m.onStart()); }
    @Override public void onResume()    { super.onResume();    safeMap(m -> m.onResume()); }
    @Override public void onPause()     { safeMap(m -> m.onPause());  super.onPause(); }
    @Override public void onStop()      { safeMap(m -> m.onStop());   super.onStop(); }
    @Override public void onLowMemory(){ super.onLowMemory(); safeMap(m -> m.onLowMemory()); }

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

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        Itinerary it = extractItinerary();
        if (it != null) displayRoute(it, parseSteps(it.getFullStepsJson()));
    }

    private void displayRoute(@NonNull Itinerary it, @NonNull List<PointOfInterest> pois) {
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
        for (PointOfInterest poi : pois) {
            LatLng pos = new LatLng(poi.getLatitude(), poi.getLongitude());
            googleMap.addMarker(new MarkerOptions().position(pos).title(poi.getName()));
            bounds.include(pos);
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80));
    }

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
