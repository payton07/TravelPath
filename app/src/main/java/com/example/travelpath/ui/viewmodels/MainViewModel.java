package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.TravelApplication;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import com.example.travelpath.domain.validation.CriteriaValidator;
import com.example.travelpath.domain.validation.ValidationResult;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;
import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel partagé entre ExploreFragment et ProfileFragment (scope Activity).
 *
 * Responsabilités :
 *   1. Détenir et exposer l'état du formulaire de recherche (destination, budget,
 *      durée, effort, intérêts, météo, lieux obligatoires).
 *   2. Persister les préférences utilisateur via {@link UserPreferencesManager}.
 *   3. Construire un {@link SearchCriteria} validé via {@link #buildCriteria()}.
 *   4. Gérer le nom d'utilisateur (lecture + écriture).
 *
 * <h2>Corrections apportées</h2>
 * <ul>
 *   <li>{@link #buildCriteria()} ajouté — la construction du critère était dans
 *       ExploreFragment, ce qui est une violation MVVM.</li>
 *   <li>{@link #setUserName(String)} ajouté — ProfileFragment appelait directement
 *       UserPreferencesManager avec un Disposable non géré.</li>
 *   <li>Persistance complète : durée, effort et météo sont maintenant sauvegardés
 *       ET rechargés au démarrage (l'original ne persistait que budget + nom).</li>
 *   <li>Les Flowable DataStore sont souscrits une seule fois dans loadPreferences()
 *       et restent actifs tant que le ViewModel existe — les mises à jour sont
 *       répercutées automatiquement sur les LiveData.</li>
 * </ul>
 */
public final class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    private final UserPreferencesManager prefs;
    private final CriteriaValidator      validator   = CriteriaValidator.create();
    private final CompositeDisposable    disposables = new CompositeDisposable();
    private final Gson                   gson        = new Gson();

    // ── État du formulaire ────────────────────────────────────────────────────

    private final MutableLiveData<String>       userName         = new MutableLiveData<>();
    private final MutableLiveData<String>       destinationCity  = new MutableLiveData<>("Paris");
    private final MutableLiveData<String>       destinationPlaceId = new MutableLiveData<>(null);
    private final MutableLiveData<List<String>> mandatoryPois    = new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<Integer>      budgetMin        = new MutableLiveData<>(20);
    private final MutableLiveData<Integer>      budgetMax        = new MutableLiveData<>(150);
    private final MutableLiveData<Integer>      durationMin      = new MutableLiveData<>(3);
    private final MutableLiveData<Integer>      durationMax      = new MutableLiveData<>(8);
    private final MutableLiveData<String>       effortLevel      = new MutableLiveData<>(SearchCriteria.EFFORT_MODERATE);
    private final MutableLiveData<List<String>> selectedInterests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<String>> weatherPreferences = new MutableLiveData<>(defaultWeather());

    // ── Constructeur ──────────────────────────────────────────────────────────

    public MainViewModel(@NonNull Application application) {
        super(application);
        prefs = UserPreferencesManager.getInstance(application);
        loadPreferences();
    }

    // =========================================================================
    // Getters LiveData
    // =========================================================================

    public LiveData<String>       getUserName()          { return userName; }
    public LiveData<String>       getDestinationCity()   { return destinationCity; }
    public LiveData<String>       getDestinationPlaceId(){ return destinationPlaceId; }
    public LiveData<List<String>> getMandatoryPois()     { return mandatoryPois; }
    public LiveData<Integer>      getBudgetMin()         { return budgetMin; }
    public LiveData<Integer>      getBudgetMax()         { return budgetMax; }
    public LiveData<Integer>      getDurationMin()       { return durationMin; }
    public LiveData<Integer>      getDurationMax()       { return durationMax; }
    public LiveData<String>       getEffortLevel()       { return effortLevel; }
    public LiveData<List<String>> getSelectedInterests() { return selectedInterests; }
    public LiveData<List<String>> getWeatherPreferences(){ return weatherPreferences; }

    // =========================================================================
    // Mutations — formulaire
    // =========================================================================

    public void setDestination(@NonNull String city, @Nullable String placeId) {
        destinationCity.setValue(city);
        destinationPlaceId.setValue(placeId);
    }

    public void addMandatoryPoi(@NonNull String poiName) {
        List<String> current = safeList(mandatoryPois);
        if (!poiName.isEmpty() && !current.contains(poiName)) {
            List<String> updated = new ArrayList<>(current);
            updated.add(poiName);
            mandatoryPois.setValue(updated);
        }
    }

    public void removeMandatoryPoi(@NonNull String poiName) {
        List<String> updated = new ArrayList<>(safeList(mandatoryPois));
        updated.remove(poiName);
        mandatoryPois.setValue(updated);
    }

    /** Met à jour le budget en mémoire ET le persiste dans DataStore. */
    public void setBudgetRange(int min, int max) {
        budgetMin.setValue(min);
        budgetMax.setValue(max);
        disposables.add(prefs.setBudgetRange(min, max)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    p  -> Timber.d("Budget persisté : %d–%d", min, max),
                    err -> Timber.w("Erreur persistance budget : %s", err.getMessage())
                ));
    }

    /** Met à jour la durée en mémoire ET la persiste dans DataStore. */
    public void setDurationRange(int min, int max) {
        durationMin.setValue(min);
        durationMax.setValue(max);
        disposables.add(prefs.setDurationRange(min, max)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    p  -> Timber.d("Durée persistée : %d–%dh", min, max),
                    err -> Timber.w("Erreur persistance durée : %s", err.getMessage())
                ));
    }

    /** Met à jour le niveau d'effort en mémoire ET le persiste. */
    public void setEffortLevel(@NonNull String effort) {
        effortLevel.setValue(effort);
        disposables.add(prefs.setEffortLevel(effort)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    p  -> Timber.d("Effort persisté : %s", effort),
                    err -> Timber.w("Erreur persistance effort : %s", err.getMessage())
                ));
    }

    public void toggleInterest(@NonNull String interest) {
        List<String> updated = new ArrayList<>(safeList(selectedInterests));
        if (updated.contains(interest)) updated.remove(interest);
        else                            updated.add(interest);
        selectedInterests.setValue(updated);
    }

    /**
     * Bascule une condition météo.
     * Au moins une condition doit rester sélectionnée — on ne retire pas le dernier élément.
     */
    public void toggleWeatherPreference(@NonNull String weather) {
        List<String> current = safeList(weatherPreferences);
        List<String> updated = new ArrayList<>(current);
        if (updated.contains(weather)) {
            if (updated.size() > 1) updated.remove(weather);
            // sinon : on ne fait rien — au moins une condition obligatoire
        } else {
            updated.add(weather);
        }
        weatherPreferences.setValue(updated);
    }

    // =========================================================================
    // Nom utilisateur
    // =========================================================================

    /**
     * Sauvegarde le nom dans DataStore et met à jour le LiveData immédiatement
     * pour que l'UI soit réactive sans attendre la confirmation de persistance.
     */
    public void setUserName(@NonNull String name) {
        if (name.isEmpty()) return;
        userName.setValue(name);                              // mise à jour immédiate
        disposables.add(prefs.setUserName(name)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    p  -> Timber.d("Nom persisté : %s", name),
                    err -> Timber.w("Erreur persistance nom : %s", err.getMessage())
                ));
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    private final MutableLiveData<Boolean> cacheClearedEvent = new MutableLiveData<>();

    public LiveData<Boolean> getCacheClearedEvent() { return cacheClearedEvent; }

    public void clearCache() {
        disposables.add(
            ((TravelApplication) getApplication()).getRepository()
                .purgeExpiredCache()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    () -> cacheClearedEvent.setValue(true),
                    err -> Timber.w("Erreur purge cache : %s", err.getMessage())
                )
        );
    }

    // =========================================================================
    // Swipe decisions → SearchCriteria mapping
    // =========================================================================

    /**
     * Maps the 5 swipe-card decisions to SearchCriteria fields.
     *
     * Card index → dimension:
     *   0 = vibe      (right → slow/easy,  left → skip)
     *   1 = pace      (right → easy,       left → high effort)
     *   2 = culture   (right → add Culture interest)
     *   3 = budget    (right → frugal €0–50, left → generous €50–200)
     *   4 = group     (right → future feature, currently no-op)
     */
    public void applySwipeDecisions(boolean[] decisions) {
        if (decisions == null || decisions.length < 5) return;

        // Vibe (0): right = slow & sunlit → easy effort
        if (decisions[0]) setEffortLevel(SearchCriteria.EFFORT_EASY);

        // Pace (1): right = relaxed → easy; left = brisk → moderate
        if (decisions[1]) setEffortLevel(SearchCriteria.EFFORT_EASY);
        else              setEffortLevel(SearchCriteria.EFFORT_MODERATE);

        // Culture (2): right = add Culture interest
        List<String> interests = new ArrayList<>(safeList(selectedInterests));
        if (decisions[2] && !interests.contains("Culture")) interests.add("Culture");
        if (!decisions[2] && interests.isEmpty())           interests.add("Nature");
        selectedInterests.setValue(interests);

        // Budget (3): right = frugal, left = more generous
        if (decisions[3]) setBudgetRange(0, 50);
        else              setBudgetRange(30, 150);

        // Group (4): scaffold for future — no-op for v1
    }

    // =========================================================================
    // Construction des critères
    // =========================================================================

    /**
     * Builds a {@link SearchCriteria} from current form state and validates it
     * through the {@link CriteriaValidator} chain of responsibility.
     *
     * Returns {@code null} on validation failure; callers check
     * {@link #getLastValidationError()} for the user-facing message.
     */
    @Nullable
    public SearchCriteria buildCriteria() {
        SearchCriteria candidate = new SearchCriteria.Builder()
                .destinationCity(orDefault(destinationCity.getValue(), "Paris"))
                .destinationPlaceId(destinationPlaceId.getValue())
                .mandatoryPois(safeList(mandatoryPois))
                .budget(
                    orDefault(budgetMin.getValue(),   20),
                    orDefault(budgetMax.getValue(),  150))
                .duration(
                    orDefault(durationMin.getValue(), 3),
                    orDefault(durationMax.getValue(), 8))
                .interests(safeList(selectedInterests))
                .effortLevel(orDefault(effortLevel.getValue(), SearchCriteria.EFFORT_MODERATE))
                .weatherPreferences(safeList(weatherPreferences))
                .build();

        ValidationResult validation = validator.validate(candidate);
        if (!validation.isValid()) {
            lastValidationError.setValue(validation.getErrorMessage());
            return null;
        }
        lastValidationError.setValue(null);
        return candidate;
    }

    /** Non-null only when the last buildCriteria() call failed validation. */
    private final MutableLiveData<String> lastValidationError = new MutableLiveData<>(null);

    public LiveData<String> getLastValidationError() { return lastValidationError; }

    // =========================================================================
    // Chargement des préférences persistées
    // =========================================================================

    /**
     * Souscrit aux Flowable DataStore une seule fois.
     * Les souscriptions restent actives tant que le ViewModel existe (onCleared).
     * Toute modification externe dans DataStore (rare) est répercutée automatiquement.
     */
    private void loadPreferences() {
        disposables.add(prefs.getUserName()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(userName::setValue,
                    err -> Timber.w("Erreur chargement nom : %s", err.getMessage())));

        disposables.add(prefs.getBudgetMin()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(budgetMin::setValue,
                    err -> Timber.w("Erreur chargement budgetMin : %s", err.getMessage())));

        disposables.add(prefs.getBudgetMax()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(budgetMax::setValue,
                    err -> Timber.w("Erreur chargement budgetMax : %s", err.getMessage())));

        // Durée
        disposables.add(prefs.getDurationMin()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> durationMin.setValue(Math.round(v)),
                    err -> Timber.w("Erreur chargement durationMin : %s", err.getMessage())));

        disposables.add(prefs.getDurationMax()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(v -> durationMax.setValue(Math.round(v)),
                    err -> Timber.w("Erreur chargement durationMax : %s", err.getMessage())));

        // Effort
        disposables.add(prefs.getEffortLevel()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(effortLevel::setValue,
                    err -> Timber.w("Erreur chargement effort : %s", err.getMessage())));

        // Météo (stockée en JSON dans DataStore)
        disposables.add(prefs.getWeatherPreferencesJson()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(json -> {
                    List<String> parsed = gson.fromJson(json,
                        new TypeToken<List<String>>(){}.getType());
                    weatherPreferences.setValue(
                        (parsed != null && !parsed.isEmpty()) ? parsed : defaultWeather());
                }, err -> Timber.w("Erreur chargement météo : %s", err.getMessage())));
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
    // Helpers
    // =========================================================================

    @NonNull
    private <T> List<T> safeList(@NonNull MutableLiveData<List<T>> liveData) {
        List<T> v = liveData.getValue();
        return v != null ? v : new ArrayList<>();
    }

    @NonNull
    private <T> T orDefault(@Nullable T value, @NonNull T defaultValue) {
        return value != null ? value : defaultValue;
    }

    @NonNull
    private static List<String> defaultWeather() {
        List<String> d = new ArrayList<>();
        d.add("SUN");
        return d;
    }
}
