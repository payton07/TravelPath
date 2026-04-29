package com.example.travelpath;

import android.app.Application;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.data.repository.TravelRepository;
import com.example.travelpath.di.AppModule;
import com.example.travelpath.domain.usecase.GenerateJourneysUseCase;
import com.example.travelpath.domain.usecase.GetSavedItinerariesUseCase;
import com.example.travelpath.domain.usecase.SaveItineraryUseCase;
import com.google.android.libraries.places.api.Places;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Point d'entrée de l'application TravelPath.
 *
 * Responsabilités :
 *   1. Initialiser les SDK tiers (Google Places, Timber)
 *   2. Construire le graphe de dépendances via {@link AppModule}
 *   3. Exposer les dépendances partagées aux ViewModels
 *
 * Accès au repository depuis un ViewModel :
 * <pre>
 *   TravelRepository repo = ((TravelApplication) getApplication()).getRepository();
 * </pre>
 */
public final class TravelApplication extends Application {

    private AppModule  module;

    /**
     * Référence conservée pour ne pas laisser un Disposable orphelin
     * si l'OS interrompt l'opération de purge en cours de route.
     * (onTerminate() n'est pas garanti en production — la mort du processus
     * libère les ressources OS de toute façon.)
     */
    @SuppressWarnings("unused")
    private Disposable purgeCacheDisposable;

    @Override
    public void onCreate() {
        super.onCreate();

        initTimber();
        initPlaces();
        initDependencies();
        scheduleCachePurge();
    }

    // =========================================================================
    // Initialisations
    // =========================================================================

    /**
     * Timber remplace Log.d / Log.w :
     *   - en DEBUG : logs visibles dans Logcat avec tag automatique
     *   - en RELEASE : no-op — aucun log n'est émis, pas besoin de ProGuard pour les supprimer
     */
    private void initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    private void initPlaces() {
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), BuildConfig.MAPS_API_KEY);
            Timber.d("Google Places SDK initialisé.");
        }
    }

    private void initDependencies() {
        module = new AppModule(this);
        Timber.d("AppModule initialisé.");
    }

    /**
     * Purge du cache Room au démarrage, exécutée sur le thread IO.
     * Non bloquante — l'app démarre normalement pendant l'opération.
     */
    private void scheduleCachePurge() {
        purgeCacheDisposable = getRepository()
                .purgeExpiredCache()
                .subscribeOn(Schedulers.io())
                .subscribe(
                    ()  -> Timber.d("Cache Room nettoyé."),
                    err -> Timber.w("Erreur purge cache : %s", err.getMessage())
                );
    }

    // =========================================================================
    // Accès aux dépendances
    // =========================================================================

    public TravelRepository getRepository() {
        return module.getRepository();
    }

    public UserPreferencesManager getPreferencesManager() {
        return module.getPreferencesManager();
    }

    public GenerateJourneysUseCase getGenerateJourneysUseCase() {
        return module.getGenerateJourneysUseCase();
    }

    public SaveItineraryUseCase getSaveItineraryUseCase() {
        return module.getSaveItineraryUseCase();
    }

    public GetSavedItinerariesUseCase getGetSavedItinerariesUseCase() {
        return module.getGetSavedItinerariesUseCase();
    }
}
