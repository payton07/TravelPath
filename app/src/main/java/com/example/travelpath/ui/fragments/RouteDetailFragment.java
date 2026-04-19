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
import com.bumptech.glide.Glide;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.data.repository.TravelRepository;
import com.example.travelpath.databinding.FragmentRouteDetailBinding;
import com.example.travelpath.databinding.ItemTimelineStepBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.JointType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.maps.android.PolyUtil;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteDetailFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_ITINERARY = "itinerary";
    private FragmentRouteDetailBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private Itinerary itinerary;
    private GoogleMap googleMap;
    private TravelRepository repository;

    public static RouteDetailFragment newInstance(Itinerary itinerary) {
        RouteDetailFragment fragment = new RouteDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_ITINERARY, itinerary);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRouteDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = ((com.example.travelpath.TravelApplication) requireActivity().getApplication()).getRepository();

        binding.btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        if (getArguments() != null) {
            itinerary = (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
            if (itinerary != null) {
                updateUI(itinerary);
                setupActions();
                
                binding.mapView.onCreate(savedInstanceState);
                binding.mapView.getMapAsync(this);
            }
        }
    }

    private void setupActions() {
        updateSaveButtonState();
        binding.btnSaveRoute.setOnClickListener(v -> toggleSave());
        binding.btnShareRoute.setOnClickListener(v -> shareItinerary());
        binding.btnExportPdf.setOnClickListener(v -> generatePdf());
    }

    private void toggleSave() {
        itinerary.setSaved(!itinerary.isSaved());
        disposables.add(repository.update(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    updateSaveButtonState();
                    String msg = itinerary.isSaved() ? "Parcours sauvegardé !" : "Parcours retiré";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }, throwable -> Toast.makeText(getContext(), "Erreur sauvegarde", Toast.LENGTH_SHORT).show()));
    }

    private void updateSaveButtonState() {
        if (itinerary.isSaved()) {
            binding.btnSaveRoute.setText("Saved");
            binding.btnSaveRoute.setIconResource(android.R.drawable.btn_star_big_on);
        } else {
            binding.btnSaveRoute.setText(getString(R.string.save));
            binding.btnSaveRoute.setIconResource(android.R.drawable.ic_menu_save);
        }
    }

    private void updateUI(Itinerary itinerary) {
        binding.tvRouteTitle.setText(itinerary.getName());
        binding.tvRouteDescription.setText(itinerary.getDescription());
        binding.tvCostDetail.setText(itinerary.getCost() + "€");
        binding.tvDurationDetail.setText(itinerary.getDuration());
        binding.tvEffortDetail.setText(itinerary.getEffort());
        binding.tvWeatherDetail.setText(itinerary.getWeather());

        if (itinerary.getImageUrl() != null && !itinerary.getImageUrl().isEmpty()) {
            Glide.with(this).load(itinerary.getImageUrl()).into(binding.ivRouteHeader);
            binding.ivRouteHeader.setVisibility(View.VISIBLE);
        }

        updateWeatherWarning(itinerary.getWeather());
        buildTimeline(itinerary);
    }

    private void updateWeatherWarning(String weather) {
        if (weather != null && (weather.contains("RAIN") || weather.contains("SNOW"))) {
            binding.cardWeatherWarning.setVisibility(View.VISIBLE);
            binding.tvWarningTitle.setText("Alerte météo : " + weather);
            binding.tvWarningDesc.setText("Des précipitations sont prévues. Prévoyez des activités en intérieur.");
        } else {
            binding.cardWeatherWarning.setVisibility(View.GONE);
        }
    }

    private void buildTimeline(Itinerary itinerary) {
        binding.timelineContainer.removeAllViews();
        
        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<PointOfInterest>>(){}.getType();
        List<PointOfInterest> poiList = gson.fromJson(itinerary.getFullStepsJson(), listType);

        if (poiList == null) return;

        for (int i = 0; i < poiList.size(); i++) {
            PointOfInterest poi = poiList.get(i);
            ItemTimelineStepBinding stepBinding = ItemTimelineStepBinding.inflate(getLayoutInflater(), binding.timelineContainer, false);
            
            stepBinding.tvStepNumber.setText(String.valueOf(i + 1));
            stepBinding.tvStepName.setText(poi.getName());
            stepBinding.tvStepTime.setText(poi.getPreferredTimeSlot().toUpperCase());

            if (poi.getOpeningHours() != null) {
                stepBinding.tvOpeningHours.setVisibility(View.VISIBLE);
                stepBinding.tvOpeningHours.setText(poi.getOpeningHours().isOpenNow() ? "Ouvert" : "Fermé");
                stepBinding.tvOpeningHours.setTextColor(poi.getOpeningHours().isOpenNow() ? 
                    ContextCompat.getColor(requireContext(), R.color.emerald_primary) : ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            }

            if (poi.getPhotoUrls() != null && !poi.getPhotoUrls().isEmpty()) {
                stepBinding.ivStepPhoto.setVisibility(View.VISIBLE);
                Glide.with(this).load(poi.getPhotoUrls().get(0)).into(stepBinding.ivStepPhoto);
            }

            binding.timelineContainer.addView(stepBinding.getRoot());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        displayRouteOnMap();
    }

    private void displayRouteOnMap() {
        if (googleMap == null || itinerary == null) return;

        if (itinerary.getEncodedPolyline() != null && !itinerary.getEncodedPolyline().isEmpty()) {
            List<LatLng> points = PolyUtil.decode(itinerary.getEncodedPolyline());
            googleMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(14) // Légèrement plus épais pour mieux voir
                    .color(ContextCompat.getColor(requireContext(), R.color.route_blue)) // Le bleu classique
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .jointType(JointType.ROUND)
                    .geodesic(true));
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<PointOfInterest>>(){}.getType();
        List<PointOfInterest> poiList = gson.fromJson(itinerary.getFullStepsJson(), listType);

        if (poiList != null && !poiList.isEmpty()) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (PointOfInterest poi : poiList) {
                LatLng pos = new LatLng(poi.getLatitude(), poi.getLongitude());
                googleMap.addMarker(new MarkerOptions().position(pos).title(poi.getName()));
                builder.include(pos);
            }
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        }
    }

    private void shareItinerary() {
        Toast.makeText(getContext(), "Génération du lien de partage...", Toast.LENGTH_SHORT).show();
        
        disposables.add(repository.shareItinerary(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(shareUrl -> {
                    Intent sendIntent = new Intent();
                    sendIntent.setAction(Intent.ACTION_SEND);
                    sendIntent.putExtra(Intent.EXTRA_TEXT, "Découvrez mon parcours TravelPath : " + shareUrl);
                    sendIntent.setType("text/plain");
                    startActivity(Intent.createChooser(sendIntent, "Partager via"));
                }, throwable -> Toast.makeText(getContext(), "Échec du partage : " + throwable.getMessage(), Toast.LENGTH_LONG).show()));
    }

    private void generatePdf() {
        Toast.makeText(getContext(), "Génération du PDF...", Toast.LENGTH_SHORT).show();
        disposables.add(repository.generatePdf(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(url -> {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setTitle("TravelPath - " + itinerary.getName());
                    request.setDescription("Téléchargement de votre itinéraire");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, itinerary.getName() + ".pdf");

                    DownloadManager manager = (DownloadManager) requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    if (manager != null) manager.enqueue(request);
                    Toast.makeText(getContext(), "Téléchargement démarré", Toast.LENGTH_SHORT).show();
                }, throwable -> Toast.makeText(getContext(), "Erreur PDF : " + throwable.getMessage(), Toast.LENGTH_SHORT).show()));
    }

    @Override public void onResume() { super.onResume(); binding.mapView.onResume(); }
    @Override public void onPause() { binding.mapView.onPause(); super.onPause(); }
    @Override public void onDestroy() { binding.mapView.onDestroy(); super.onDestroy(); disposables.clear(); }
    @Override public void onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory(); }
}
