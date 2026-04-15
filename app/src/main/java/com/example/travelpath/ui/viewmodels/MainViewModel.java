package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.data.preferences.UserPreferencesManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final UserPreferencesManager preferencesManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<String> userName = new MutableLiveData<>();
    private final MutableLiveData<Integer> budgetMin = new MutableLiveData<>();
    private final MutableLiveData<Integer> budgetMax = new MutableLiveData<>();
    private final MutableLiveData<Integer> durationMin = new MutableLiveData<>(4);
    private final MutableLiveData<Integer> durationMax = new MutableLiveData<>(8);
    private final MutableLiveData<String> effortLevel = new MutableLiveData<>("Moderate");
    private final MutableLiveData<List<String>> selectedInterests = new MutableLiveData<>(new ArrayList<>());

    private final MutableLiveData<List<String>> weatherPreferences = new MutableLiveData<>(new ArrayList<>(List.of("SUN")));

    public MainViewModel(@NonNull Application application) {
        super(application);
        preferencesManager = UserPreferencesManager.getInstance(application);
        loadPreferences();
    }

    public LiveData<List<String>> getWeatherPreferences() { return weatherPreferences; }
    public LiveData<Integer> getDurationMin() { return durationMin; }
    public LiveData<Integer> getDurationMax() { return durationMax; }
    public LiveData<String> getEffortLevel() { return effortLevel; }

    public void setDurationRange(int min, int max) {
        durationMin.setValue(min);
        durationMax.setValue(max);
    }

    public void setEffortLevel(String effort) {
        effortLevel.setValue(effort);
    }

    public void toggleWeatherPreference(String weather) {
        List<String> current = weatherPreferences.getValue();
        if (current != null) {
            List<String> updated = new ArrayList<>(current);
            if (updated.contains(weather)) {
                if (updated.size() > 1) { // On garde au moins une option
                    updated.remove(weather);
                }
            } else {
                updated.add(weather);
            }
            weatherPreferences.setValue(updated);
        }
    }

    private void loadPreferences() {
        disposables.add(preferencesManager.getUserName()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(userName::setValue));

        disposables.add(preferencesManager.getBudgetMin()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(budgetMin::setValue));

        disposables.add(preferencesManager.getBudgetMax()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(budgetMax::setValue));
    }

    public LiveData<String> getUserName() { return userName; }
    public LiveData<Integer> getBudgetMin() { return budgetMin; }
    public LiveData<Integer> getBudgetMax() { return budgetMax; }
    public LiveData<List<String>> getSelectedInterests() { return selectedInterests; }

    public void setBudgetRange(int min, int max) {
        disposables.add(preferencesManager.setBudgetRange(min, max)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    public void toggleInterest(String interest) {
        List<String> current = selectedInterests.getValue();
        if (current != null) {
            if (current.contains(interest)) {
                current.remove(interest);
            } else {
                current.add(interest);
            }
            selectedInterests.setValue(current);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
