package com.example.travelpath.di;

import android.content.Context;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.database.AppDatabase;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.data.remote.FirebaseDataSource;
import com.example.travelpath.data.repository.TravelRepository;
import com.example.travelpath.domain.usecase.GenerateJourneysUseCase;
import com.example.travelpath.domain.usecase.GetSavedItinerariesUseCase;
import com.example.travelpath.domain.usecase.SaveItineraryUseCase;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.gson.Gson;

/**
 * Manual DI container — single composition root for the entire app.
 *
 * Design patterns:
 *  - Composition Root: all dependencies are wired here, eliminating hidden
 *    coupling between classes and enabling easy test overrides.
 *  - Dependency Inversion: callers depend on interfaces / use cases,
 *    not on concrete infrastructure (Room, Firebase, Gson).
 *
 * How to access dependencies in a ViewModel:
 * <pre>
 *   AppModule module = ((TravelApplication) getApplication()).getModule();
 *   GenerateJourneysUseCase useCase = module.getGenerateJourneysUseCase();
 * </pre>
 */
public final class AppModule {

    private final TravelRepository         repository;
    private final UserPreferencesManager   preferencesManager;
    private final GenerateJourneysUseCase  generateJourneysUseCase;
    private final SaveItineraryUseCase     saveItineraryUseCase;
    private final GetSavedItinerariesUseCase getSavedItinerariesUseCase;

    public AppModule(Context appContext) {
        Gson               gson       = new Gson();
        ItineraryDao       dao        = AppDatabase.getInstance(appContext).itineraryDao();
        FirebaseDataSource dataSource = new FirebaseDataSource(FirebaseFunctions.getInstance("europe-west1"), gson);

        this.repository              = new TravelRepository(dao, dataSource);
        this.preferencesManager      = UserPreferencesManager.getInstance(appContext);

        // Use Cases — constructed once, shared across all ViewModels
        this.generateJourneysUseCase    = new GenerateJourneysUseCase(repository);
        this.saveItineraryUseCase       = new SaveItineraryUseCase(repository);
        this.getSavedItinerariesUseCase = new GetSavedItinerariesUseCase(repository);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public TravelRepository            getRepository()                 { return repository; }
    public UserPreferencesManager      getPreferencesManager()         { return preferencesManager; }
    public GenerateJourneysUseCase     getGenerateJourneysUseCase()    { return generateJourneysUseCase; }
    public SaveItineraryUseCase        getSaveItineraryUseCase()       { return saveItineraryUseCase; }
    public GetSavedItinerariesUseCase  getGetSavedItinerariesUseCase() { return getSavedItinerariesUseCase; }
}
