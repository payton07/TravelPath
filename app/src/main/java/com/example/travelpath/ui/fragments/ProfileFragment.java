package com.example.travelpath.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.travelpath.databinding.FragmentProfileBinding;
import com.example.travelpath.ui.viewmodels.MainViewModel;
import com.example.travelpath.ui.viewmodels.SavedRoutesViewModel;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private MainViewModel mainViewModel;
    private SavedRoutesViewModel savedRoutesViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Récupérer les ViewModels
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        savedRoutesViewModel = new ViewModelProvider(requireActivity()).get(SavedRoutesViewModel.class);

        setupActions();
        observeViewModels();
    }

    private void setupActions() {
        binding.btnEditProfile.setOnClickListener(v -> showEditNameDialog());
    }

    private void observeViewModels() {
        // Observer le nom de l'utilisateur
        mainViewModel.getUserName().observe(getViewLifecycleOwner(), name -> {
            if (name != null && !name.isEmpty()) {
                binding.tvUserName.setText(name);
            }
        });

        // Observer le nombre d'itinéraires sauvegardés
        savedRoutesViewModel.getSavedItineraries().observe(getViewLifecycleOwner(), itineraries -> {
            if (itineraries != null) {
                binding.tvSavedCount.setText(String.valueOf(itineraries.size()));
            }
        });
    }

    private void showEditNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Edit Profile Name");

        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        
        // Pré-remplir avec le nom actuel
        String currentName = mainViewModel.getUserName().getValue();
        if (currentName != null) {
            input.setText(currentName);
        }
        
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                // Pour sauvegarder le nom dans les préférences persistantes, 
                // il faudrait idéalement appeler UserPreferencesManager, 
                // mais le MainViewModel ne semble pas exposer de méthode setUserName.
                // Je vais utiliser directement le PreferencesManager pour persister la donnée.
                com.example.travelpath.TravelApplication app = (com.example.travelpath.TravelApplication) requireActivity().getApplication();
                app.getPreferencesManager().setUserName(newName)
                        .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                        .subscribe();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
