package com.example.travelpath.ui.fragments;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.travelpath.R;
import com.example.travelpath.data.FirebaseManager;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.FragmentRouteDetailBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.json.JSONArray;
import org.json.JSONObject;

public class RouteDetailFragment extends Fragment implements OnMapReadyCallback {

    private static final String ARG_ITINERARY = "itinerary";
    private FragmentRouteDetailBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private Itinerary itinerary;
    private GoogleMap googleMap;

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

        binding.btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        if (getArguments() != null) {
            itinerary = (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
            if (itinerary != null) {
                updateUI(itinerary);
                setupPdfExport(itinerary);
                
                // Initialisation de la Map
                binding.mapView.onCreate(savedInstanceState);
                binding.mapView.getMapAsync(this);
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        
        // Configuration du style de la carte (Sombre si possible, ou standard)
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        
        displayMarkers();
    }

    private void displayMarkers() {
        if (googleMap == null || itinerary == null || itinerary.getPoiCoordinatesJson() == null) return;

        try {
            JSONArray coords = new JSONArray(itinerary.getPoiCoordinatesJson());
            if (coords.length() == 0) return;

            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            String[] stepNames = itinerary.getSteps().split(" → ");

            for (int i = 0; i < coords.length(); i++) {
                JSONObject obj = coords.getJSONObject(i);
                LatLng position = new LatLng(obj.getDouble("lat"), obj.getDouble("lng"));
                
                String title = (i < stepNames.length) ? stepNames[i] : "POI " + (i + 1);
                
                googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title((i + 1) + ". " + title));
                
                boundsBuilder.include(position);
            }

            // Centrer la caméra sur tous les points avec un padding
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateUI(Itinerary itinerary) {
        binding.tvRouteTitle.setText(itinerary.getName());
        binding.tvDistance.setText(itinerary.getDuration()); // On affiche la durée dans le badge pour l'instant
    }

    private void setupPdfExport(Itinerary itinerary) {
        binding.btnExportPdf.setOnClickListener(v -> {
            Toast.makeText(getContext(), getString(R.string.pdf_generating), Toast.LENGTH_SHORT).show();
            binding.btnExportPdf.setEnabled(false);
            binding.btnExportPdf.setAlpha(0.5f);

            disposables.add(FirebaseManager.getInstance().generatePDF(itinerary)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            url -> {
                                Toast.makeText(getContext(), getString(R.string.pdf_success), Toast.LENGTH_SHORT).show();
                                downloadPDF(url, itinerary.getName());
                                binding.btnExportPdf.setEnabled(true);
                                binding.btnExportPdf.setAlpha(1.0f);
                            },
                            throwable -> {
                                Toast.makeText(getContext(), getString(R.string.pdf_error, throwable.getMessage()), Toast.LENGTH_LONG).show();
                                binding.btnExportPdf.setEnabled(true);
                                binding.btnExportPdf.setAlpha(1.0f);
                            }
                    ));
        });
    }

    private void downloadPDF(String url, String filenameBase) {
        if (getContext() == null) return;
        String safeFilename = filenameBase.replaceAll("[^a-zA-Z0-9.-]", "_") + ".pdf";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle(getString(R.string.pdf_download_title))
                .setDescription(getString(R.string.pdf_download_desc, filenameBase))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename);

        DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) downloadManager.enqueue(request);
    }

    // Gestion obligatoire du cycle de vie pour MapView
    @Override public void onResume() { super.onResume(); binding.mapView.onResume(); }
    @Override public void onPause() { super.onPause(); binding.mapView.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); if (binding != null) binding.mapView.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory(); }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}
