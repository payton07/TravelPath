package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.FragmentSavedBinding;
import com.example.travelpath.ui.adapter.RouteAdapter;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;
import com.example.travelpath.ui.widget.MessageBanner;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;

/**
 * Liste des itinéraires sauvegardés par l'utilisateur.
 *
 * Corrections :
 *   - Repository retiré — toggleSave() passe par SavedRoutesViewModel.
 *   - Navigation déléguée à MainActivity.navigateTo().
 *   - Adaptateur passe en mode COMPACT avec le nouveau RouteAdapter.
 */
public final class SavedFragment extends Fragment implements OnMapReadyCallback {

    private FragmentSavedBinding  binding;
    private SavedRoutesViewModel  viewModel;
    private RouteAdapter          adapter;
    private GoogleMap             googleMap;
    private List<Itinerary>       pendingItineraries;

    // ── Cycle de vie ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(SavedRoutesViewModel.class);

        setupRecyclerView();
        binding.mapWorldView.onCreate(savedInstanceState);
        binding.mapWorldView.getMapAsync(this);
        observeViewModel();
    }

    @Override public void onStart()      { super.onStart();     if (binding != null) binding.mapWorldView.onStart(); }
    @Override public void onResume()     { super.onResume();    if (binding != null) binding.mapWorldView.onResume(); }
    @Override public void onPause()      { if (binding != null) binding.mapWorldView.onPause();  super.onPause(); }
    @Override public void onStop()       { if (binding != null) binding.mapWorldView.onStop();   super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (binding != null) binding.mapWorldView.onLowMemory(); }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) binding.mapWorldView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (binding != null) {
            binding.mapWorldView.onDestroy();
            binding.rvSavedRoutes.setAdapter(null);
        }
        super.onDestroyView();
        binding = null;
        googleMap = null;
    }

    // =========================================================================
    // Map
    // =========================================================================

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        map.getUiSettings().setAllGesturesEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        map.setOnMarkerClickListener(marker -> {
            Itinerary it = (Itinerary) marker.getTag();
            if (it != null) {
                ((MainActivity) requireActivity())
                    .navigateTo(RouteDetailFragment.newInstance(it), "detail");
            }
            return true;
        });

        if (pendingItineraries != null) {
            updateMapMarkers(pendingItineraries);
            pendingItineraries = null;
        }
    }

    private void updateMapMarkers(@NonNull List<Itinerary> itineraries) {
        if (googleMap == null) {
            pendingItineraries = itineraries;
            return;
        }
        googleMap.clear();
        if (itineraries.isEmpty()) return;

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        boolean hasAny = false;

        for (Itinerary it : itineraries) {
            LatLng pos = extractFirstCoordinate(it);
            if (pos == null) continue;
            String title = it.getDestinationCity() != null ? it.getDestinationCity() : it.getName();
            Marker marker = googleMap.addMarker(new MarkerOptions().position(pos).title(title));
            if (marker != null) marker.setTag(it);
            bounds.include(pos);
            hasAny = true;
        }

        if (hasAny) {
            try {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 60));
            } catch (Exception ignored) {}
        }
    }

    @Nullable
    private LatLng extractFirstCoordinate(@NonNull Itinerary it) {
        String json = it.getPoiCoordinatesJson();
        if (json == null || json.isEmpty()) return null;
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            if (arr.size() == 0) return null;
            double lat = arr.get(0).getAsJsonObject().get("lat").getAsDouble();
            double lng = arr.get(0).getAsJsonObject().get("lng").getAsDouble();
            return new LatLng(lat, lng);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Setup
    // =========================================================================

    private void setupRecyclerView() {
        adapter = new RouteAdapter(RouteAdapter.VIEW_TYPE_COMPACT, new RouteAdapter.OnRouteActionListener() {
            @Override
            public void onRouteClick(Itinerary itinerary) {
                ((MainActivity) requireActivity())
                    .navigateTo(RouteDetailFragment.newInstance(itinerary), "detail");
            }

            @Override
            public void onLikeClick(Itinerary itinerary) {
                // Déléguer au ViewModel — pas de repository dans le fragment
                viewModel.toggleSave(itinerary);
            }
        });

        binding.rvSavedRoutes.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSavedRoutes.setAdapter(adapter);
        attachSwipeToDelete();
    }

    private void attachSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_ID) return;
                Itinerary itinerary = adapter.getItemAt(pos);
                viewModel.deleteItinerary(itinerary);
                ((MainActivity) requireActivity()).showMessage(
                        MessageBanner.Type.SUCCESS,
                        getString(R.string.route_removed));
            }
        }).attachToRecyclerView(binding.rvSavedRoutes);
    }

    // =========================================================================
    // Observation
    // =========================================================================

    private void observeViewModel() {
        viewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            boolean empty = itineraries == null || itineraries.isEmpty();
            binding.tvEmptyMessage.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.rvSavedRoutes.setVisibility(empty ? View.GONE    : View.VISIBLE);
            binding.cardWorldMap.setVisibility(empty  ? View.GONE    : View.VISIBLE);

            if (!empty) {
                adapter.submitList(itineraries);
                updateMapMarkers(itineraries);
            }
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof com.example.travelpath.ui.viewmodels.UiState.Error) {
                ((MainActivity) requireActivity()).showMessage(
                        com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                        ((com.example.travelpath.ui.viewmodels.UiState.Error<?>) state).getMessage());
            }
        });
    }
}
