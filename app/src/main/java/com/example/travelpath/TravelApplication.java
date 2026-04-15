package com.example.travelpath;

import android.app.Application;
import android.util.Log;
import com.example.travelpath.data.FirebaseManager;
import com.example.travelpath.data.repository.TravelRepository;

/**
 * Point d'entrée de l'application. 
 * Utilisée pour l'injection de dépendances manuelle et l'initialisation des singletons.
 */
public class TravelApplication extends Application {
    private static final String TAG = "TravelApplication";

    private static TravelRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "L'application TravelPath a démarré avec le thème Emerald Voyage.");

        // Initialisation de Google Places avec la clé sécurisée de local.properties
        if (!com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initialize(getApplicationContext(), BuildConfig.MAPS_API_KEY);
        }

        // Initialisation de Firebase via notre Manager
        FirebaseManager.getInstance();
        Log.d(TAG, "FirebaseManager initialisé.");
        
        // Initialisation de la DB Room et du Repository
        repository = new TravelRepository(this);
        Log.d(TAG, "TravelRepository initialisé.");
    }

    public static TravelRepository getRepository() {
        return repository;
    }
}
