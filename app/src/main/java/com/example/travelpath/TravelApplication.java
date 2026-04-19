package com.example.travelpath;

import android.app.Application;
import android.util.Log;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.data.repository.TravelRepository;
import com.example.travelpath.di.AppModule;
import com.google.android.libraries.places.api.Places;

/**
 * Point d'entrée de l'application TravelPath.
 *
 * Responsabilités :
 *   1. Initialiser les SDK tiers (Google Places, Firebase implicite)
 *   2. Construire le graphe de dépendances via {@link AppModule}
 *   3. Exposer les dépendances aux ViewModels via des getters statiques
 *
 * Les ViewModels accèdent au repository via :
 * <pre>
 *   TravelRepository repo = ((TravelApplication) getApplication()).getRepository();
 * </pre>
 * ou via une ViewModelFactory injectée.
 */
public final class TravelApplication extends Application {

    private static final String TAG = "TravelApplication";

    private AppModule module;

    @Override
    public void onCreate() {
        super.onCreate();

        // ── Google Places SDK ─────────────────────────────────────────────────
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), BuildConfig.MAPS_API_KEY);
            Log.d(TAG, "Google Places SDK initialisé.");
        }

        // ── Graphe de dépendances ─────────────────────────────────────────────
        module = new AppModule(this);
        Log.d(TAG, "AppModule initialisé — TravelPath démarré.");

        // ── Nettoyage du cache expiré au démarrage ────────────────────────────
        getRepository()
            .purgeExpiredCache()
            .subscribe(
                () -> Log.d(TAG, "Cache Room nettoyé."),
                err -> Log.w(TAG, "Erreur nettoyage cache : " + err.getMessage())
            );
    }

    // ── Accès aux dépendances ─────────────────────────────────────────────────

    public TravelRepository getRepository() {
        return module.getRepository();
    }

    public UserPreferencesManager getPreferencesManager() {
        return module.getPreferencesManager();
    }
}
