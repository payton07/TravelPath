package com.example.travelpath.ui.fragments;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.databinding.FragmentRouteDetailBinding;
import com.example.travelpath.databinding.ItemTimelineStepBinding;
import com.example.travelpath.ui.viewmodels.RouteDetailViewModel;
import com.example.travelpath.ui.viewmodels.UiState;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.PolyUtil;
import timber.log.Timber;
import java.util.List;

/**
 * Détails riches d'un itinéraire.
 *
 * <h2>Corrections appliquées</h2>
 * <ul>
 *   <li>Repository retiré — toutes les opérations passent par {@link RouteDetailViewModel}.</li>
 *   <li>CompositeDisposable supprimé — les Completable/Single sont dans le ViewModel.</li>
 *   <li>MapView cycle de vie complet : onStart/onStop ajoutés, binding nul à destrView.</li>
 *   <li>Gson instanciée une seule fois dans le ViewModel, pas ici.</li>
 *   <li>Navigation Back déléguée au dispatcher.</li>
 * </ul>
 */
public final class RouteDetailFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_ITINERARY = "itinerary";

    private FragmentRouteDetailBinding binding;
    private RouteDetailViewModel       viewModel;
    private GoogleMap                  googleMap;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static RouteDetailFragment newInstance(@NonNull Itinerary itinerary) {
        RouteDetailFragment f = new RouteDetailFragment();
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
        binding = FragmentRouteDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ViewModelProvider.Factory factory = RouteDetailViewModel.Factory.create(requireActivity());
        viewModel = new ViewModelProvider(this, factory).get(RouteDetailViewModel.class);

        Itinerary itinerary = extractItinerary();
        if (itinerary == null) {
            Timber.w("RouteDetailFragment : itinéraire null, fermeture.");
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        viewModel.setItinerary(itinerary);

        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());

        setupMapView(savedInstanceState);
        setupActions();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        // MapView doit être détruit AVANT de nullifier binding
        if (binding != null) {
            binding.mapView.onDestroy();
        }
        super.onDestroyView();
        binding = null;
    }

    // ── Cycle de vie MapView ──────────────────────────────────────────────────

    @Override public void onStart()      { super.onStart();      safeMap(m -> m.onStart()); }
    @Override public void onResume()     { super.onResume();      safeMap(m -> m.onResume()); }
    @Override public void onPause()      { safeMap(m -> m.onPause());   super.onPause(); }
    @Override public void onStop()       { safeMap(m -> m.onStop());    super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory();  safeMap(m -> m.onLowMemory()); }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        safeMap(m -> m.onSaveInstanceState(outState));
    }

    /** Exécute une action sur MapView seulement si binding est encore valide. */
    private void safeMap(MapViewAction action) {
        if (binding != null) action.run(binding.mapView);
    }
    private interface MapViewAction { void run(com.google.android.gms.maps.MapView mapView); }

    // =========================================================================
    // Initialisation
    // =========================================================================

    private void setupMapView(@Nullable Bundle savedInstanceState) {
        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);
    }

    private void setupActions() {
        binding.btnSaveRoute.setOnClickListener(v  -> viewModel.toggleSave());
        binding.btnShareRoute.setOnClickListener(v -> viewModel.shareItinerary());
        binding.btnExportPdf.setOnClickListener(v  -> viewModel.generatePdf());
    }

    // =========================================================================
    // Observations
    // =========================================================================

    private void observeViewModel() {
        viewModel.getItinerary().observe(getViewLifecycleOwner(), this::renderItinerary);

        viewModel.getSaveState().observe(getViewLifecycleOwner(), saved -> {
            refreshSaveButton(saved);
            String msg = saved ? getString(R.string.route_saved) : getString(R.string.route_removed);
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.getShareState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof UiState.Success) {
                launchShareIntent(((UiState.Success<String>) state).getData());
            } else if (state instanceof UiState.Error) {
                Toast.makeText(getContext(),
                    ((UiState.Error) state).getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getPdfState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof UiState.Loading) {
                Toast.makeText(getContext(), R.string.generating_pdf, Toast.LENGTH_SHORT).show();
            } else if (state instanceof UiState.Success) {
                enqueueDownload(((UiState.Success<String>) state).getData());
            } else if (state instanceof UiState.Error) {
                Toast.makeText(getContext(),
                    ((UiState.Error) state).getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // =========================================================================
    // Rendu de l'itinéraire
    // =========================================================================

    private void renderItinerary(@NonNull Itinerary it) {
        binding.tvRouteTitle.setText(it.getName());
        binding.tvRouteDescription.setText(it.getDescription());
        binding.tvCostDetail.setText(String.format("%s€", it.getCost()));
        binding.tvDurationDetail.setText(it.getDuration());
        binding.tvEffortDetail.setText(it.getEffort());
        binding.tvWeatherDetail.setText(it.getWeather());

        if (it.getImageUrl() != null && !it.getImageUrl().isEmpty()) {
            binding.ivRouteHeader.setVisibility(View.VISIBLE);
            Glide.with(this).load(it.getImageUrl()).into(binding.ivRouteHeader);
        }

        refreshSaveButton(it.isSaved());
        renderWeatherWarning(it.getWeather());

        List<PointOfInterest> pois = viewModel.parseFullSteps(it.getFullStepsJson());
        buildTimeline(pois);

        if (googleMap != null) displayRoute(it, pois);
    }

    private void renderWeatherWarning(@Nullable String weather) {
        boolean hasAlert = weather != null
                && (weather.contains("RAIN") || weather.contains("SNOW"));
        binding.cardWeatherWarning.setVisibility(hasAlert ? View.VISIBLE : View.GONE);
        if (hasAlert) {
            binding.tvWarningTitle.setText(getString(R.string.weather_alert_title, weather));
            binding.tvWarningDesc.setText(R.string.weather_alert_desc);
        }
    }

    private void buildTimeline(@Nullable List<PointOfInterest> pois) {
        binding.timelineContainer.removeAllViews();
        if (pois == null || pois.isEmpty()) return;

        for (int i = 0; i < pois.size(); i++) {
            PointOfInterest poi = pois.get(i);
            ItemTimelineStepBinding step = ItemTimelineStepBinding.inflate(
                getLayoutInflater(), binding.timelineContainer, false);

            step.tvStepNumber.setText(String.valueOf(i + 1));
            step.tvStepName.setText(poi.getName());
            step.tvStepTime.setText(poi.getPreferredTimeSlot() != null
                ? poi.getPreferredTimeSlot().toUpperCase() : "");

            bindOpeningHours(step, poi);
            bindStepPhoto(step, poi);

            binding.timelineContainer.addView(step.getRoot());
        }
    }

    private void bindOpeningHours(ItemTimelineStepBinding step, PointOfInterest poi) {
        if (poi.getOpeningHours() == null) {
            step.tvOpeningHours.setVisibility(View.GONE);
            return;
        }
        step.tvOpeningHours.setVisibility(View.VISIBLE);
        boolean open = poi.getOpeningHours().isOpenNow();
        step.tvOpeningHours.setText(open ? R.string.open : R.string.closed);
        step.tvOpeningHours.setTextColor(ContextCompat.getColor(requireContext(),
            open ? R.color.emerald_primary : android.R.color.holo_red_dark));
    }

    private void bindStepPhoto(ItemTimelineStepBinding step, PointOfInterest poi) {
        String photoUrl = poi.getPrimaryPhotoUrl();
        if (photoUrl != null) {
            step.ivStepPhoto.setVisibility(View.VISIBLE);
            Glide.with(this).load(photoUrl).into(step.ivStepPhoto);
        } else {
            step.ivStepPhoto.setVisibility(View.GONE);
        }
    }

    private void refreshSaveButton(boolean saved) {
        binding.btnSaveRoute.setText(saved ? R.string.saved : R.string.save);
        binding.btnSaveRoute.setIconResource(saved
            ? android.R.drawable.btn_star_big_on
            : android.R.drawable.ic_menu_save);
    }

    // =========================================================================
    // Google Maps
    // =========================================================================

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        // Si l'itinéraire est déjà chargé au moment où la carte est prête
        Itinerary it = viewModel.getItinerary().getValue();
        if (it != null) {
            displayRoute(it, viewModel.parseFullSteps(it.getFullStepsJson()));
        }
    }

    private void displayRoute(@NonNull Itinerary it, @Nullable List<PointOfInterest> pois) {
        if (googleMap == null) return;

        // Tracé polyline
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

        // Marqueurs et cadrage
        if (pois == null || pois.isEmpty()) return;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (PointOfInterest poi : pois) {
            LatLng pos = new LatLng(poi.getLatitude(), poi.getLongitude());
            googleMap.addMarker(new MarkerOptions().position(pos).title(poi.getName()));
            bounds.include(pos);
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100));
    }

    // =========================================================================
    // Actions
    // =========================================================================

    private void launchShareIntent(@NonNull String shareUrl) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT,
            getString(R.string.share_message, shareUrl));
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)));
    }

    private void enqueueDownload(@NonNull String url) {
        Itinerary it = viewModel.getItinerary().getValue();
        if (it == null) return;

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(getString(R.string.pdf_title, it.getName()));
        req.setDescription(getString(R.string.pdf_desc));
        req.setNotificationVisibility(
            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(
            Environment.DIRECTORY_DOWNLOADS, it.getName() + ".pdf");

        DownloadManager dm = (DownloadManager)
            requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(req);
            Toast.makeText(getContext(), R.string.download_started, Toast.LENGTH_SHORT).show();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Nullable
    private Itinerary extractItinerary() {
        if (getArguments() == null) return null;
        return (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
    }
}
