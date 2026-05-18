package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.domain.usecase.GetSavedItinerariesUseCase;
import com.example.travelpath.domain.usecase.SaveItineraryUseCase;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;
import java.util.List;

/**
 * ViewModel for SavedFragment (and ProfileFragment's saved count badge).
 * Activity-scoped so both fragments share one database subscription.
 *
 * Improvements:
 *  - Delegates to GetSavedItinerariesUseCase and SaveItineraryUseCase
 *    (same save logic as RouteViewModel — DRY, no duplication).
 *  - Exposes full UiState so SavedFragment can display error banners,
 *    not just silently fail.
 *  - savedItineraries LiveData kept for ProfileFragment's count badge
 *    without requiring it to parse UiState.
 */
public final class SavedRoutesViewModel extends AndroidViewModel {

    private final GetSavedItinerariesUseCase getSavedUseCase;
    private final SaveItineraryUseCase       saveUseCase;
    private final CompositeDisposable        disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<List<Itinerary>>> uiState =
            new MutableLiveData<>(UiState.loading());

    /** Separate LiveData for ProfileFragment which only needs the list count. */
    private final MutableLiveData<List<Itinerary>> savedItineraries = new MutableLiveData<>();

    public SavedRoutesViewModel(@NonNull Application application) {
        super(application);
        TravelApplication app = (TravelApplication) application;
        this.getSavedUseCase = app.getGetSavedItinerariesUseCase();
        this.saveUseCase     = app.getSaveItineraryUseCase();
        observeSavedItineraries();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public LiveData<UiState<List<Itinerary>>> getUiState()         { return uiState; }
    public LiveData<List<Itinerary>>          getSavedItineraries() { return savedItineraries; }

    // =========================================================================
    // Actions
    // =========================================================================

    /** Toggles saved state (same behavior as RouteViewModel — shared use case). */
    public void toggleSave(@NonNull Itinerary itinerary) {
        disposables.add(saveUseCase.toggleSave(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("Saved toggled: %s → isSaved=%b",
                                itinerary.getName(), itinerary.isSaved()),
                    err -> {
                        Timber.w("toggleSave error: %s", err.getMessage());
                        uiState.setValue(UiState.error("Unable to update saved state."));
                    }
                ));
    }

    /** Permanently removes an itinerary from Room. */
    public void deleteItinerary(@NonNull Itinerary itinerary) {
        disposables.add(saveUseCase.delete(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("Deleted: %s", itinerary.getName()),
                    err -> {
                        Timber.w("Delete error: %s", err.getMessage());
                        uiState.setValue(UiState.error("Unable to delete itinerary."));
                    }
                ));
    }

    // =========================================================================
    // Room observation
    // =========================================================================

    /**
     * Subscribes once to the Room Flowable.
     * Emits a new list on every insert / update / delete — no manual refresh needed.
     */
    private void observeSavedItineraries() {
        disposables.add(getSavedUseCase.execute()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    itineraries -> {
                        savedItineraries.setValue(itineraries);
                        uiState.setValue(
                            itineraries.isEmpty()
                                ? UiState.empty()
                                : UiState.success(itineraries));
                    },
                    err -> {
                        Timber.w("Load saved error: %s", err.getMessage());
                        uiState.setValue(UiState.error("Unable to load saved itineraries."));
                    }
                ));
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
