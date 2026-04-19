package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.travelpath.R;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.databinding.FragmentSavedBinding;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;

public class SavedFragment extends Fragment {

    private FragmentSavedBinding binding;
    private SavedRoutesViewModel viewModel;
    private RouteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSavedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SavedRoutesViewModel.class);

        setupRecyclerView();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new RouteAdapter(new RouteAdapter.OnRouteClickListener() {
            @Override
            public void onRouteClick(Itinerary itinerary) {
                RouteDetailFragment detailFragment = RouteDetailFragment.newInstance(itinerary);
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, detailFragment)
                        .addToBackStack(null)
                        .commit();
            }

            @Override
            public void onLikeClick(Itinerary itinerary) {
                itinerary.setSaved(!itinerary.isSaved());
                ((com.example.travelpath.TravelApplication) requireActivity().getApplication()).getRepository().update(itinerary).subscribe();
            }
        });
        binding.rvSavedRoutes.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSavedRoutes.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries == null || itineraries.isEmpty()) {
                binding.tvEmptyMessage.setVisibility(View.VISIBLE);
                binding.rvSavedRoutes.setVisibility(View.GONE);
            } else {
                binding.tvEmptyMessage.setVisibility(View.GONE);
                binding.rvSavedRoutes.setVisibility(View.VISIBLE);
                adapter.setItineraries(itineraries);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
