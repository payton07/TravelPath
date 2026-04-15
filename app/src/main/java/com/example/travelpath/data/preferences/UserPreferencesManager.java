package com.example.travelpath.data.preferences;

import android.content.Context;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class UserPreferencesManager {
    private static final String DATASTORE_NAME = "user_prefs";
    private static final Preferences.Key<String> USER_NAME = PreferencesKeys.stringKey("user_name");
    private static final Preferences.Key<Integer> BUDGET_MIN = PreferencesKeys.intKey("budget_min");
    private static final Preferences.Key<Integer> BUDGET_MAX = PreferencesKeys.intKey("budget_max");

    private static UserPreferencesManager instance;
    private final RxDataStore<Preferences> dataStore;

    private UserPreferencesManager(Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(context.getApplicationContext(), DATASTORE_NAME).build();
    }

    public static synchronized UserPreferencesManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserPreferencesManager(context);
        }
        return instance;
    }

    public Flowable<String> getUserName() {
        return dataStore.data().map(prefs -> prefs.get(USER_NAME) != null ? prefs.get(USER_NAME) : "Traveler");
    }

    public Single<Preferences> setUserName(String name) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(USER_NAME, name);
            return Single.just(mutablePreferences);
        });
    }

    public Flowable<Integer> getBudgetMin() {
        return dataStore.data().map(prefs -> prefs.get(BUDGET_MIN) != null ? prefs.get(BUDGET_MIN) : 20);
    }

    public Flowable<Integer> getBudgetMax() {
        return dataStore.data().map(prefs -> prefs.get(BUDGET_MAX) != null ? prefs.get(BUDGET_MAX) : 150);
    }

    public Single<Preferences> setBudgetRange(int min, int max) {
        return dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(BUDGET_MIN, min);
            mutablePreferences.set(BUDGET_MAX, max);
            return Single.just(mutablePreferences);
        });
    }
}
