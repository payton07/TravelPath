package com.example.travelpath.ui.fragments;

import android.app.DownloadManager;
import com.example.travelpath.MainActivity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import androidx.core.content.FileProvider;
import com.example.travelpath.ui.util.StoryCardGenerator;
import java.io.File;
import java.io.FileOutputStream;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.res.ColorStateList;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.ui.adapter.RouteAdapter;
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
        checkConnectivity();
    }

    private void checkConnectivity() {
        if (!com.example.travelpath.utils.NetworkUtils.isOnline(requireContext())) {
            ((MainActivity) requireActivity()).showMessage(
                    com.example.travelpath.ui.widget.MessageBanner.Type.INFO,
                    getString(R.string.offline_mode_active));
            binding.cardWeatherWarning.setVisibility(View.VISIBLE);
            binding.tvWarningTitle.setText(R.string.offline_title);
            binding.tvWarningDesc.setText(R.string.offline_desc);
        }
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
        binding.btnShareStory.setOnClickListener(v -> shareStoryCard());

        binding.btnStartRoute.setOnClickListener(v -> {
            Itinerary it = viewModel.getItinerary().getValue();
            if (it != null) {
                ((MainActivity) requireActivity()).navigateTo(
                    NavigationFragment.newInstance(it), "navigation");
            }
        });

        binding.btnMapFullscreen.setOnClickListener(v -> {
            Itinerary it = viewModel.getItinerary().getValue();
            if (it != null) {
                ((MainActivity) requireActivity()).navigateTo(
                    FullScreenMapFragment.newInstance(it), "fullscreen_map");
            }
        });
    }

    // =========================================================================
    // Observations
    // =========================================================================

    private void observeViewModel() {
        viewModel.getItinerary().observe(getViewLifecycleOwner(), this::renderItinerary);

        viewModel.getSaveState().observe(getViewLifecycleOwner(), saved -> {
            refreshSaveButton(saved);
            String msg = saved ? getString(R.string.route_saved) : getString(R.string.route_removed);
            com.example.travelpath.ui.widget.MessageBanner.Type type = saved
                    ? com.example.travelpath.ui.widget.MessageBanner.Type.SUCCESS
                    : com.example.travelpath.ui.widget.MessageBanner.Type.INFO;
            ((MainActivity) requireActivity()).showMessage(type, msg);
        });

        viewModel.getShareState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof UiState.Success) {
                launchShareIntent(((UiState.Success<String>) state).getData());
            } else if (state instanceof UiState.Error) {
                ((MainActivity) requireActivity()).showMessage(
                        com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                        ((UiState.Error) state).getMessage());
            }
        });

        viewModel.getPdfState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof UiState.Loading) {
                ((MainActivity) requireActivity()).showMessage(
                        com.example.travelpath.ui.widget.MessageBanner.Type.INFO,
                        getString(R.string.generating_pdf));
            } else if (state instanceof UiState.Success) {
                enqueueDownload(((UiState.Success<String>) state).getData());
            } else if (state instanceof UiState.Error) {
                ((MainActivity) requireActivity()).showMessage(
                        com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                        ((UiState.Error) state).getMessage());
            }
        });
    }

    // =========================================================================
    // Rendu de l'itinéraire
    // =========================================================================

    private void renderItinerary(@NonNull Itinerary it) {
        binding.tvRouteTitle.setText(it.getName());
        binding.tvRouteDescription.setText(it.getDescription());
        binding.tvCostDetail.setText(String.format("~%s€", it.getCost()));
        binding.tvDurationDetail.setText(it.getDuration());
        binding.tvEffortDetail.setText(it.getEffort());
        binding.tvWeatherDetail.setText(RouteAdapter.formatWeather(requireContext(), it.getWeather()));

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
        // Déclenche l'alerte seulement si le temps est EXCLUSIVEMENT mauvais (pluie/neige)
        // et non pas si la liste contient simplement ces mots parmi d'autres (compatibilité).
        boolean isBadWeather = weather != null && (weather.equals("RAIN") || weather.equals("SNOW"));
        
        binding.cardWeatherWarning.setVisibility(isBadWeather ? View.VISIBLE : View.GONE);
        if (isBadWeather) {
            binding.tvWarningTitle.setText(getString(R.string.weather_alert_title, weather));
            binding.tvWarningDesc.setText(R.string.weather_alert_desc);
        }
    }

    private static final int[] STOP_COLORS = {
        R.color.color_butter, R.color.color_sky,
        R.color.color_blush,  R.color.color_mint, R.color.color_lilac
    };

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

            int stopColor = ContextCompat.getColor(requireContext(), STOP_COLORS[i % STOP_COLORS.length]);
            step.tvStepNumber.setBackgroundTintList(ColorStateList.valueOf(stopColor));

            bindOpeningHours(step, poi);
            bindCrowdLevel(step, poi);
            bindStepPhoto(step, poi);

            final PointOfInterest finalPoi = poi;
            step.getRoot().setOnClickListener(v ->
                ((MainActivity) requireActivity()).navigateTo(
                    PoiDetailFragment.newInstance(finalPoi), "poi_detail"));

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

    private void bindCrowdLevel(ItemTimelineStepBinding step, PointOfInterest poi) {
        String crowd = poi.getCrowdLevel();
        if (crowd == null) {
            step.tvCrowdLevel.setVisibility(View.GONE);
            return;
        }
        step.tvCrowdLevel.setVisibility(View.VISIBLE);
        int labelRes, colorRes;
        switch (crowd) {
            case "LOW":  labelRes = R.string.crowd_low;    colorRes = R.color.color_mint;   break;
            case "HIGH": labelRes = R.string.crowd_high;   colorRes = R.color.color_blush;  break;
            default:     labelRes = R.string.crowd_medium; colorRes = R.color.color_butter; break;
        }
        step.tvCrowdLevel.setText(labelRes);
        step.tvCrowdLevel.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), colorRes)));
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
        int iconColor = ContextCompat.getColor(requireContext(),
            saved ? R.color.color_accent : R.color.color_ink);
        binding.btnSaveRoute.setIconTint(ColorStateList.valueOf(iconColor));
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

    private void shareStoryCard() {
        Itinerary it = viewModel.getItinerary().getValue();
        if (it == null) return;

        List<PointOfInterest> pois = viewModel.parseFullSteps(it.getFullStepsJson());
        Bitmap bmp = StoryCardGenerator.generate(requireContext(), it, pois);

        try {
            File dir  = new File(requireContext().getCacheDir(), "story_cards");
            dir.mkdirs();
            String safe = it.getName().replaceAll("[^a-zA-Z0-9]", "_");
            File file = new File(dir, safe + ".png");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
            bmp.recycle();

            Uri uri = FileProvider.getUriForFile(
                    requireContext(), "com.example.travelpath.fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.story_card_share_via)));
        } catch (Exception e) {
            ((MainActivity) requireActivity()).showMessage(
                    com.example.travelpath.ui.widget.MessageBanner.Type.ERROR,
                    getString(R.string.story_card_error));
            Timber.w("Story card error: %s", e.getMessage());
        }
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
            ((MainActivity) requireActivity()).showMessage(
                    com.example.travelpath.ui.widget.MessageBanner.Type.SUCCESS,
                    getString(R.string.download_started));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @Nullable
    private Itinerary extractItinerary() {
        if (getArguments() == null) return null;
        return (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
    }
}
