package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.PointOfInterest;
import com.example.travelpath.data.repository.TravelRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel de RouteDetailFragment.
 *
 * Responsabilités :
 *   1. Détenir l'itinéraire affiché et exposer ses mutations (sauvegarde).
 *   2. Gérer les opérations réseau : partage, export PDF.
 *   3. Parser le JSON fullSteps → List&lt;PointOfInterest&gt; (Gson instancié une seule fois).
 *
 * Ce ViewModel était absent dans l'original : RouteDetailFragment appelait directement
 * le Repository avec un CompositeDisposable local — les opérations en cours étaient
 * annulées à chaque rotation d'écran, et les erreurs perdues.
 *
 * <h2>Factory interne</h2>
 * Nécessaire car {@link AndroidViewModel} reçoit l'Application via le constructeur.
 * Utiliser {@link Factory#create(ViewModelStoreOwner)} depuis le fragment.
 */
public final class RouteDetailViewModel extends AndroidViewModel {

    private final TravelRepository    repository;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Gson                gson        = new Gson();

    // ── LiveData exposées ─────────────────────────────────────────────────────

    /** Itinéraire courant — mis à jour après toggleSave() pour refléter le nouvel état. */
    private final MutableLiveData<Itinerary>       itinerary   = new MutableLiveData<>();

    /** true = sauvegardé, false = retiré des favoris. Émis après chaque toggleSave réussi. */
    private final MutableLiveData<Boolean>         saveState   = new MutableLiveData<>();

    /** État du partage : Loading | Success(url) | Error(message). */
    private final MutableLiveData<UiState<String>> shareState  = new MutableLiveData<>();

    /** État de l'export PDF : Loading | Success(url) | Error(message). */
    private final MutableLiveData<UiState<String>> pdfState    = new MutableLiveData<>();

    // ── Constructeur ──────────────────────────────────────────────────────────

    public RouteDetailViewModel(@NonNull Application application) {
        super(application);
        repository = ((TravelApplication) application).getRepository();
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public LiveData<Itinerary>       getItinerary()  { return itinerary; }
    public LiveData<Boolean>         getSaveState()  { return saveState; }
    public LiveData<UiState<String>> getShareState() { return shareState; }
    public LiveData<UiState<String>> getPdfState()   { return pdfState; }

    // =========================================================================
    // Initialisation
    // =========================================================================

    /**
     * Injecte l'itinéraire au démarrage.
     * Idempotent : si le même objet est déjà présent (rotation), on ne redéclenche pas.
     */
    public void setItinerary(@NonNull Itinerary it) {
        Itinerary current = itinerary.getValue();
        if (current != null && current.getId() == it.getId()) return;
        itinerary.setValue(it);
    }

    // =========================================================================
    // Actions
    // =========================================================================

    /** Bascule isSaved et persiste dans Room. */
    public void toggleSave() {
        Itinerary it = itinerary.getValue();
        if (it == null) return;

        it.setSaved(!it.isSaved());
        disposables.add(repository.save(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> {
                        itinerary.setValue(it);           // notifie les observers
                        saveState.setValue(it.isSaved());
                        Timber.d("toggleSave OK : %s → %b", it.getName(), it.isSaved());
                    },
                    err -> Timber.w("Erreur toggleSave : %s", err.getMessage())
                ));
    }

    /** Génère un lien de partage via la Cloud Function. */
    public void shareItinerary() {
        Itinerary it = itinerary.getValue();
        if (it == null) return;

        shareState.setValue(UiState.loading());
        disposables.add(repository.shareItinerary(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    url  -> shareState.setValue(UiState.success(url)),
                    err  -> {
                        Timber.w("Erreur partage : %s", err.getMessage());
                        shareState.setValue(UiState.error(
                            "Impossible de générer le lien : " + err.getMessage()));
                    }
                ));
    }

    /** Génère un PDF et retourne l'URL de téléchargement. */
    public void generatePdf() {
        Itinerary it = itinerary.getValue();
        if (it == null) return;

        pdfState.setValue(UiState.loading());
        disposables.add(repository.generatePdf(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    url  -> pdfState.setValue(UiState.success(url)),
                    err  -> {
                        Timber.w("Erreur PDF : %s", err.getMessage());
                        pdfState.setValue(UiState.error(
                            "Impossible de générer le PDF : " + err.getMessage()));
                    }
                ));
    }

    // =========================================================================
    // Parsing JSON → PointOfInterest
    // =========================================================================

    /**
     * Parse le JSON fullSteps stocké dans l'entité Room en liste de POIs.
     * La Gson est instanciée une seule fois dans ce ViewModel (pas à chaque rendu).
     *
     * @return Liste de POIs, ou liste vide si JSON null/invalide.
     */
    @NonNull
    public List<PointOfInterest> parseFullSteps(@Nullable String fullStepsJson) {
        if (fullStepsJson == null || fullStepsJson.isEmpty()) return new ArrayList<>();
        try {
            List<PointOfInterest> result = gson.fromJson(fullStepsJson,
                new TypeToken<List<PointOfInterest>>(){}.getType());
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            Timber.w("Erreur parsing fullSteps : %s", e.getMessage());
            return new ArrayList<>();
        }
    }

    // =========================================================================
    // Cycle de vie
    // =========================================================================

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Factory pour instancier RouteDetailViewModel avec l'Application correcte.
     *
     * Usage dans le fragment :
     * <pre>
     *   ViewModelProvider.Factory factory = RouteDetailViewModel.Factory.create(requireActivity());
     *   viewModel = new ViewModelProvider(this, factory).get(RouteDetailViewModel.class);
     * </pre>
     */
    public static final class Factory {
        private Factory() {}

        public static ViewModelProvider.Factory create(@NonNull ViewModelStoreOwner owner) {
            return new ViewModelProvider.AndroidViewModelFactory(
                ((Application) ((android.content.Context) owner)
                    .getApplicationContext()));
        }
    }
}
