package com.example.travelpath.ui.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import com.example.travelpath.MainActivity;
import com.example.travelpath.R;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.databinding.FragmentPoiDetailBinding;
import com.example.travelpath.ui.widget.MessageBanner;
import java.util.List;
import java.util.Locale;

public final class PoiDetailFragment extends Fragment {

    private static final String ARG_POI = "poi";

    private FragmentPoiDetailBinding binding;

    public static PoiDetailFragment newInstance(@NonNull PointOfInterest poi) {
        PoiDetailFragment f = new PoiDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_POI, poi);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPoiDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnBack.setOnClickListener(v ->
            requireActivity().getOnBackPressedDispatcher().onBackPressed());

        PointOfInterest poi = extractPoi();
        if (poi == null) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        renderPoi(poi);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void renderPoi(@NonNull PointOfInterest poi) {
        binding.tvPoiName.setText(poi.getName());
        binding.tvPoiCategory.setText(poi.getCategory() != null ? poi.getCategory() : "");

        if (poi.getRating() > 0) {
            binding.tvPoiRating.setText(String.format(Locale.getDefault(), "★ %.1f", poi.getRating()));
        } else {
            binding.tvPoiRating.setVisibility(View.GONE);
        }

        PointOfInterest.OpeningHours hours = poi.getOpeningHours();
        if (hours != null) {
            binding.tvPoiOpenStatus.setVisibility(View.VISIBLE);
            boolean open = hours.isOpenNow();
            binding.tvPoiOpenStatus.setText(open ? R.string.open : R.string.closed);
            binding.tvPoiOpenStatus.setTextColor(ContextCompat.getColor(requireContext(),
                open ? R.color.emerald_primary : android.R.color.holo_red_dark));

            List<String> weekdays = hours.getWeekdayText();
            if (weekdays != null && !weekdays.isEmpty()) {
                binding.layoutHours.setVisibility(View.VISIBLE);
                for (String line : weekdays) {
                    TextView tv = new TextView(requireContext());
                    tv.setText(line);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_ink));
                    tv.setTextSize(13f);
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tv.setPadding(0, 4, 0, 4);
                    binding.hoursContainer.addView(tv);
                }
            }
        }

        if (poi.getAverageDurationHours() > 0) {
            binding.tvPoiDuration.setText(String.format(Locale.getDefault(), "~%.0fh",
                poi.getAverageDurationHours()));
        } else {
            binding.tvPoiDuration.setText("—");
        }

        String photoUrl = poi.getPrimaryPhotoUrl();
        if (photoUrl != null) {
            Glide.with(this).load(photoUrl).into(binding.ivPoiPhoto);
        }

        binding.btnGetDirections.setOnClickListener(v -> launchMaps(poi));
    }

    private void launchMaps(@NonNull PointOfInterest poi) {
        String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)",
            poi.getLatitude(), poi.getLongitude(),
            poi.getLatitude(), poi.getLongitude(),
            Uri.encode(poi.getName()));
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            ((MainActivity) requireActivity()).showMessage(
                MessageBanner.Type.ERROR, getString(R.string.error_maps_unavailable));
        }
    }

    @Nullable
    private PointOfInterest extractPoi() {
        if (getArguments() == null) return null;
        return (PointOfInterest) getArguments().getSerializable(ARG_POI);
    }
}
