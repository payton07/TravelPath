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
import com.example.travelpath.data.FirebaseManager;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.FragmentRouteDetailBinding;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class RouteDetailFragment extends Fragment {

    private static final String ARG_ITINERARY = "itinerary";
    private FragmentRouteDetailBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

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
            Itinerary itinerary = (Itinerary) getArguments().getSerializable(ARG_ITINERARY);
            if (itinerary != null) {
                updateUI(itinerary);
                setupPdfExport(itinerary);
            }
        }
    }

    private void updateUI(Itinerary itinerary) {
        binding.tvRouteTitle.setText(itinerary.getName());
        // On pourrait remplir le reste de l'UI ici (steps, cost, etc.)
    }

    private void setupPdfExport(Itinerary itinerary) {
        binding.btnExportPdf.setOnClickListener(v -> {
            Toast.makeText(getContext(), "Génération du PDF en cours...", Toast.LENGTH_SHORT).show();
            binding.btnExportPdf.setEnabled(false); // Empêche le double-clic
            binding.btnExportPdf.setAlpha(0.5f);

            disposables.add(FirebaseManager.getInstance().generatePDF(itinerary)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            url -> {
                                Toast.makeText(getContext(), "PDF généré, téléchargement...", Toast.LENGTH_SHORT).show();
                                downloadPDF(url, itinerary.getName());
                                binding.btnExportPdf.setEnabled(true);
                                binding.btnExportPdf.setAlpha(1.0f);
                            },
                            throwable -> {
                                Toast.makeText(getContext(), "Erreur : " + throwable.getMessage(), Toast.LENGTH_LONG).show();
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
                .setTitle("Itinéraire TravelPath")
                .setDescription("Téléchargement du parcours : " + filenameBase)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFilename)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        DownloadManager downloadManager = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.enqueue(request);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
        binding = null;
    }
}
