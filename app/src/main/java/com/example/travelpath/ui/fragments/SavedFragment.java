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

/**
 * Liste des itinéraires sauvegardés par l'utilisateur.
 *
 * Corrections :
 *   - Repository retiré — toggleSave() passe par SavedRoutesViewModel.
 *   - Navigation déléguée à MainActivity.navigateTo().
 *   - Adaptateur passe en mode COMPACT avec le nouveau RouteAdapter.
 */
public final class SavedFragment extends Fragment {

    private FragmentSavedBinding  binding;
    private SavedRoutesViewModel  viewModel;
    private RouteAdapter          adapter;

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
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        // Détacher l'adaptateur avant de nullifier binding
        binding.rvSavedRoutes.setAdapter(null);
        super.onDestroyView();
        binding = null;
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

            if (!empty) {
                adapter.submitList(itineraries);
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
