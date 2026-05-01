package com.example.travelpath.data.preferences;

import android.content.Context;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Gestion des préférences persistantes de l'utilisateur via DataStore.
 *
 * Couvre tous les critères de recherche réutilisables entre sessions :
 * nom, budget, durée, niveau d'effort et préférences météo.
 *
 * Singleton thread-safe — obtenir via {@link #getInstance(Context)}.
 */
public final class UserPreferencesManager {

    private static final String DATASTORE_NAME = "user_prefs";

    // ── Clés DataStore ────────────────────────────────────────────────────────
    private static final Preferences.Key<String>  KEY_USER_NAME       = PreferencesKeys.stringKey("user_name");
    private static final Preferences.Key<Integer> KEY_BUDGET_MIN      = PreferencesKeys.intKey("budget_min");
    private static final Preferences.Key<Integer> KEY_BUDGET_MAX      = PreferencesKeys.intKey("budget_max");
    private static final Preferences.Key<Float>   KEY_DURATION_MIN    = PreferencesKeys.floatKey("duration_min");
    private static final Preferences.Key<Float>   KEY_DURATION_MAX    = PreferencesKeys.floatKey("duration_max");
    private static final Preferences.Key<String>  KEY_EFFORT          = PreferencesKeys.stringKey("effort_level");
    private static final Preferences.Key<String>  KEY_WEATHER         = PreferencesKeys.stringKey("weather_prefs"); // JSON array
    private static final Preferences.Key<Boolean> KEY_ONBOARDING_DONE = PreferencesKeys.booleanKey("onboarding_complete");

    // ── Valeurs par défaut ────────────────────────────────────────────────────
    private static final String  DEFAULT_USER_NAME    = "Traveler";
    private static final int     DEFAULT_BUDGET_MIN   = 20;
    private static final int     DEFAULT_BUDGET_MAX   = 150;
    private static final float   DEFAULT_DURATION_MIN = 3f;
    private static final float   DEFAULT_DURATION_MAX = 8f;
    private static final String  DEFAULT_EFFORT       = "Easy";
    private static final String  DEFAULT_WEATHER      = "[]";

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile UserPreferencesManager instance;
    private final RxDataStore<Preferences> dataStore;

    private UserPreferencesManager(Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(
                context.getApplicationContext(), DATASTORE_NAME
        ).build();
    }

    public static UserPreferencesManager getInstance(Context context) {
        if (instance == null) {
            synchronized (UserPreferencesManager.class) {
                if (instance == null) {
                    instance = new UserPreferencesManager(context);
                }
            }
        }
        return instance;
    }

    // =========================================================================
    // Nom utilisateur
    // =========================================================================

    public Flowable<String> getUserName() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_USER_NAME), DEFAULT_USER_NAME));
    }

    public Single<Preferences> setUserName(String name) {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_USER_NAME, name);
            return Single.just(m);
        });
    }

    // =========================================================================
    // Budget
    // =========================================================================

    public Flowable<Integer> getBudgetMin() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_BUDGET_MIN), DEFAULT_BUDGET_MIN));
    }

    public Flowable<Integer> getBudgetMax() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_BUDGET_MAX), DEFAULT_BUDGET_MAX));
    }

    public Single<Preferences> setBudgetRange(int min, int max) {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_BUDGET_MIN, min);
            m.set(KEY_BUDGET_MAX, max);
            return Single.just(m);
        });
    }

    // =========================================================================
    // Durée
    // =========================================================================

    public Flowable<Float> getDurationMin() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_DURATION_MIN), DEFAULT_DURATION_MIN));
    }

    public Flowable<Float> getDurationMax() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_DURATION_MAX), DEFAULT_DURATION_MAX));
    }

    public Single<Preferences> setDurationRange(float min, float max) {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_DURATION_MIN, min);
            m.set(KEY_DURATION_MAX, max);
            return Single.just(m);
        });
    }

    // =========================================================================
    // Effort
    // =========================================================================

    public Flowable<String> getEffortLevel() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_EFFORT), DEFAULT_EFFORT));
    }

    public Single<Preferences> setEffortLevel(String level) {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_EFFORT, level);
            return Single.just(m);
        });
    }

    // =========================================================================
    // Préférences météo (stocké comme JSON string d'un tableau)
    // =========================================================================

    public Flowable<String> getWeatherPreferencesJson() {
        return dataStore.data().map(p -> orDefault(p.get(KEY_WEATHER), DEFAULT_WEATHER));
    }

    public Single<Preferences> setWeatherPreferencesJson(String json) {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_WEATHER, json);
            return Single.just(m);
        });
    }

    // =========================================================================
    // Onboarding
    // =========================================================================

    /** Returns true if the user has already completed the onboarding flow. */
    public Single<Boolean> isOnboardingComplete() {
        return dataStore.data()
                .map(p -> {
                    Boolean done = p.get(KEY_ONBOARDING_DONE);
                    return done != null && done;
                })
                .firstOrError();
    }

    public Single<Preferences> setOnboardingComplete() {
        return dataStore.updateDataAsync(p -> {
            MutablePreferences m = p.toMutablePreferences();
            m.set(KEY_ONBOARDING_DONE, true);
            return Single.just(m);
        });
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private <T> T orDefault(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
