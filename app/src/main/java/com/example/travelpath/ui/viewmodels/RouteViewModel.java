package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.data.repository.TravelRepository;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;
import java.util.List;

/**
 * ViewModel de RoutesFragment — génération et gestion des 3 itinéraires.
 *
 * <h2>Corrections apportées</h2>
 * <ul>
 *   <li>3 LiveData séparés ({@code isGenerating}, {@code routes}, {@code errorMessage})
 *       remplacés par un {@link UiState} unifié — état toujours cohérent.</li>
 *   <li>{@link #toggleSave(Itinerary)} ajouté — était dans le fragment dans l'original.</li>
 *   <li>{@link #regenerateRoutes(SearchCriteria)} ajouté pour forcer un nouvel
 *       appel réseau en ignorant le cache (après un délike, ou ajustement de critères).</li>
 *   <li>Guard contre les doubles appels : si une génération est déjà en cours,
 *       {@link #generateRoutes(SearchCriteria)} est ignoré.</li>
 * </ul>
 */
public final class RouteViewModel extends AndroidViewModel {

    private final TravelRepository repository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** État unique de l'UI — Loading | Success | Empty | Error. */
    private final MutableLiveData<UiState<List<Itinerary>>> uiState =
            new MutableLiveData<>(UiState.loading());

    /** Critères courants — conservés pour permettre la regénération. */
    private SearchCriteria currentCriteria;

    public RouteViewModel(@NonNull Application application) {
        super(application);
        repository = ((TravelApplication) application).getRepository();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public LiveData<UiState<List<Itinerary>>> getUiState() { return uiState; }

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Lance la génération des itinéraires.
     * Ignoré si une génération est déjà en cours (protection double-tap).
     */
    public void generateRoutes(@NonNull SearchCriteria criteria) {
        if (uiState.getValue() instanceof UiState.Loading) {
            Timber.d("Génération déjà en cours — appel ignoré.");
            return;
        }
        currentCriteria = criteria;
        fetchRoutes(criteria, false);
    }

    /**
     * Force une regénération en ignorant le cache local (après un délike, etc.).
     * Utilise les derniers critères connus si {@code criteria} est null.
     */
    public void regenerateRoutes(@NonNull SearchCriteria criteria) {
        currentCriteria = criteria;
        fetchRoutes(criteria, true);
    }

    /**
     * Bascule l'état sauvegardé d'un itinéraire.
     * Met à jour Room via le repository — pas dans le fragment.
     */
    public void toggleSave(@NonNull Itinerary itinerary) {
        itinerary.setSaved(!itinerary.isSaved());
        disposables.add(repository.save(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("Itinéraire %s : isSaved = %b",
                                itinerary.getName(), itinerary.isSaved()),
                    err -> Timber.w("Erreur toggleSave : %s", err.getMessage())
                ));
    }

    // =========================================================================
    // Fetch interne
    // =========================================================================

    private void fetchRoutes(@NonNull SearchCriteria criteria, boolean forceRefresh) {
        uiState.setValue(UiState.loading());

        disposables.add(
            (forceRefresh
                ? repository.regenerateJourneys(criteria)
                : repository.generateJourneys(criteria))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                routes -> {
                    if (routes.isEmpty()) {
                        uiState.setValue(UiState.empty());
                    } else {
                        uiState.setValue(UiState.success(routes));
                    }
                },
                err -> {
                    Timber.w("Erreur génération : %s", err.getMessage());
                    uiState.setValue(UiState.error(
                        err.getMessage() != null
                            ? err.getMessage()
                            : "Erreur inconnue lors de la génération."));
                }
            )
        );
    }

    // =========================================================================
    // Cycle de vie
    // =========================================================================

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}
