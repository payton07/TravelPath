package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.domain.usecase.GenerateJourneysUseCase;
import com.example.travelpath.domain.usecase.SaveItineraryUseCase;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;
import java.util.List;

/**
 * ViewModel for RoutesFragment.
 *
 * Delegates all business logic to injected Use Cases — it knows nothing
 * about Room, Firebase, or retry policies (Single Responsibility).
 *
 * Improvements:
 *  - Uses GenerateJourneysUseCase (includes retry + Clean Architecture boundary).
 *  - Uses SaveItineraryUseCase (shared behavior with SavedRoutesViewModel — DRY).
 *  - ViewPager2 page position preserved across configuration changes.
 *  - Guard against duplicate in-flight generate calls.
 */
public final class RouteViewModel extends AndroidViewModel {

    private final GenerateJourneysUseCase generateUseCase;
    private final SaveItineraryUseCase    saveUseCase;
    private final CompositeDisposable     disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<List<Itinerary>>> uiState =
            new MutableLiveData<>();

    /** Persists carousel position across rotation — restored by RoutesFragment. */
    private final MutableLiveData<Integer> currentPage = new MutableLiveData<>(0);

    private SearchCriteria currentCriteria;

    public RouteViewModel(@NonNull Application application) {
        super(application);
        TravelApplication app = (TravelApplication) application;
        this.generateUseCase = app.getGenerateJourneysUseCase();
        this.saveUseCase     = app.getSaveItineraryUseCase();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public LiveData<UiState<List<Itinerary>>> getUiState()    { return uiState; }
    public LiveData<Integer>                  getCurrentPage() { return currentPage; }

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Generates itineraries from cache or network.
     * Ignored if a generation is already in-flight (double-tap guard).
     */
    public void generateRoutes(@NonNull SearchCriteria criteria) {
        if (uiState.getValue() instanceof UiState.Loading && currentCriteria != null) {
            Timber.d("Generation in progress — ignoring duplicate call.");
            return;
        }
        currentCriteria = criteria;
        fetch(generateUseCase.execute(criteria));
    }

    /** Skips local cache and forces a fresh network call. */
    public void regenerateRoutes(@NonNull SearchCriteria criteria) {
        currentCriteria = criteria;
        fetch(generateUseCase.forceRefresh(criteria));
    }

    /** Toggles saved state via SaveItineraryUseCase (shared logic — DRY). */
    public void toggleSave(@NonNull Itinerary itinerary) {
        disposables.add(saveUseCase.toggleSave(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("Saved toggled: %s → isSaved=%b",
                                itinerary.getName(), itinerary.isSaved()),
                    err -> Timber.w("toggleSave error: %s", err.getMessage())
                ));
    }

    /** Persists the carousel page so it survives rotation. */
    public void setCurrentPage(int page) {
        currentPage.setValue(page);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void fetch(io.reactivex.rxjava3.core.Single<List<Itinerary>> source) {
        uiState.setValue(UiState.loading());
        disposables.add(
            source
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    routes -> uiState.setValue(
                        routes.isEmpty() ? UiState.empty() : UiState.success(routes)),
                    err    -> {
                        Timber.w("Generation error: %s", err.getMessage());
                        uiState.setValue(UiState.error("Unable to generate itineraries. Please try again."));
                    }
                )
        );
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
