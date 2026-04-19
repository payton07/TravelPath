package com.example.travelpath.di;

import android.content.Context;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.database.AppDatabase;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.data.remote.FirebaseDataSource;
import com.example.travelpath.data.repository.TravelRepository;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;

/**
 * Conteneur d'injection de dépendances manuel (pas de Dagger/Hilt requis ici).
 *
 * Instancié une seule fois dans {@link com.example.travelpath.TravelApplication}.
 * Toutes les dépendances sont construites ici — les classes métier ne connaissent
 * pas Firebase, Gson ou Room directement, elles reçoivent leurs dépendances.
 *
 * Avantages vs singletons dispersés :
 *  - Graphe de dépendances visible en un seul endroit
 *  - Remplacement facile en tests (passer un AppModule de test)
 *  - Aucune référence statique dans les classes métier
 */
public final class AppModule {

    private final TravelRepository      repository;
    private final UserPreferencesManager preferencesManager;

    public AppModule(Context appContext) {
        Gson              gson       = new Gson();
        ItineraryDao      dao        = AppDatabase.getInstance(appContext).itineraryDao();
        FirebaseDataSource dataSource = new FirebaseDataSource(FirebaseFunctions.getInstance(), gson);

        this.repository         = new TravelRepository(dao, dataSource);
        this.preferencesManager = UserPreferencesManager.getInstance(appContext);
    }

    public TravelRepository       getRepository()          { return repository; }
    public UserPreferencesManager getPreferencesManager()  { return preferencesManager; }
}
