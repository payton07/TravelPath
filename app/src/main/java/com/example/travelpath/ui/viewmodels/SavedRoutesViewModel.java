package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.repository.TravelRepository;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;
import java.util.List;

/**
 * ViewModel de SavedFragment (et ProfileFragment pour le compteur).
 * Scope Activity — les deux fragments partagent les mêmes données sans double requête.
 *
 * <h2>Corrections apportées</h2>
 * <ul>
 *   <li>{@code isLoading} supprimé — remplacé par {@link UiState} unifié qui
 *       expose aussi les erreurs (l'original les ignorait silencieusement).</li>
 *   <li>{@link #toggleSave(Itinerary)} ajouté — était dans SavedFragment.</li>
 *   <li>Le Flowable Room est souscrit une seule fois et reste actif tant que le
 *       ViewModel existe. Chaque nouvelle écriture Room (insert/update/delete)
 *       émet automatiquement une nouvelle liste — pas besoin de recharger manuellement.</li>
 *   <li>{@link #getSavedItineraries()} conservé pour compatibilité avec ProfileFragment
 *       qui observe uniquement la liste (pas l'UiState complet).</li>
 * </ul>
 */
public final class SavedRoutesViewModel extends AndroidViewModel {

    private final TravelRepository    repository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<UiState<List<Itinerary>>> uiState =
            new MutableLiveData<>(UiState.loading());

    /** Exposé séparément pour ProfileFragment qui n'a besoin que du compteur. */
    private final MutableLiveData<List<Itinerary>> savedItineraries = new MutableLiveData<>();

    public SavedRoutesViewModel(@NonNull Application application) {
        super(application);
        repository = ((TravelApplication) application).getRepository();
        observeSavedItineraries();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public LiveData<UiState<List<Itinerary>>> getUiState()          { return uiState; }
    public LiveData<List<Itinerary>>          getSavedItineraries()  { return savedItineraries; }

    // =========================================================================
    // Actions
    // =========================================================================

    /**
     * Bascule l'état sauvegardé d'un itinéraire.
     * Si on le "délike" depuis SavedFragment, il disparaîtra automatiquement
     * de la liste car le Flowable Room émet une nouvelle valeur après chaque update.
     */
    public void toggleSave(@NonNull Itinerary itinerary) {
        itinerary.setSaved(!itinerary.isSaved());
        disposables.add(repository.save(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("toggleSave OK : %s → isSaved=%b",
                                itinerary.getName(), itinerary.isSaved()),
                    err -> {
                        Timber.w("Erreur toggleSave : %s", err.getMessage());
                        uiState.setValue(UiState.error(
                            "Impossible de mettre à jour la sauvegarde."));
                    }
                ));
    }

    public void deleteItinerary(@NonNull Itinerary itinerary) {
        disposables.add(repository.delete(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ()  -> Timber.d("Itinéraire supprimé : %s", itinerary.getName()),
                    err -> {
                        Timber.w("Erreur suppression : %s", err.getMessage());
                        uiState.setValue(UiState.error("Impossible de supprimer l'itinéraire."));
                    }
                ));
    }

    // =========================================================================
    // Observation du Flowable Room
    // =========================================================================

    /**
     * Souscrit une seule fois au Flowable Room.
     *
     * Le Flowable {@code getSavedItineraries()} émet automatiquement une nouvelle
     * liste à chaque modification de la table (insert, update, delete) — pas besoin
     * de recharger manuellement après un toggleSave ou deleteItinerary.
     */
    private void observeSavedItineraries() {
        disposables.add(repository.getSavedItineraries()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    itineraries -> {
                        savedItineraries.setValue(itineraries);
                        if (itineraries.isEmpty()) {
                            uiState.setValue(UiState.empty());
                        } else {
                            uiState.setValue(UiState.success(itineraries));
                        }
                    },
                    err -> {
                        Timber.w("Erreur chargement favoris : %s", err.getMessage());
                        uiState.setValue(UiState.error(
                            "Impossible de charger vos itinéraires sauvegardés."));
                    }
                ));
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
