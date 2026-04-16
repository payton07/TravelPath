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
    private final MutableLiveData<String> destinationCity = new MutableLiveData<>("Paris");
    private final MutableLiveData<String> destinationPlaceId = new MutableLiveData<>();
    private final MutableLiveData<List<String>> mandatoryPois = new MutableLiveData<>(new ArrayList<>());
    
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

    public LiveData<String> getUserName() { return userName; }
    public LiveData<String> getDestinationCity() { return destinationCity; }
    public LiveData<String> getDestinationPlaceId() { return destinationPlaceId; }
    public LiveData<List<String>> getMandatoryPois() { return mandatoryPois; }
    public LiveData<Integer> getBudgetMin() { return budgetMin; }
    public LiveData<Integer> getBudgetMax() { return budgetMax; }
    public LiveData<Integer> getDurationMin() { return durationMin; }
    public LiveData<Integer> getDurationMax() { return durationMax; }
    public LiveData<String> getEffortLevel() { return effortLevel; }
    public LiveData<List<String>> getSelectedInterests() { return selectedInterests; }
    public LiveData<List<String>> getWeatherPreferences() { return weatherPreferences; }

    public void setDestination(String city, String placeId) {
        destinationCity.setValue(city);
        destinationPlaceId.setValue(placeId);
    }

    public void addMandatoryPoi(String poiName) {
        List<String> current = mandatoryPois.getValue();
        if (current != null && !poiName.isEmpty() && !current.contains(poiName)) {
            current.add(poiName);
            mandatoryPois.setValue(new ArrayList<>(current));
        }
    }

    public void removeMandatoryPoi(String poiName) {
        List<String> current = mandatoryPois.getValue();
        if (current != null) {
            current.remove(poiName);
            mandatoryPois.setValue(new ArrayList<>(current));
        }
    }

    public void setBudgetRange(int min, int max) {
        budgetMin.setValue(min);
        budgetMax.setValue(max);
        disposables.add(preferencesManager.setBudgetRange(min, max)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    public void setDurationRange(int min, int max) {
        durationMin.setValue(min);
        durationMax.setValue(max);
    }

    public void setEffortLevel(String effort) {
        effortLevel.setValue(effort);
    }

    public void toggleInterest(String interest) {
        List<String> current = selectedInterests.getValue();
        if (current != null) {
            List<String> updated = new ArrayList<>(current);
            if (updated.contains(interest)) {
                updated.remove(interest);
            } else {
                updated.add(interest);
            }
            selectedInterests.setValue(updated);
        }
    }

    public void toggleWeatherPreference(String weather) {
        List<String> current = weatherPreferences.getValue();
        if (current != null) {
            List<String> updated = new ArrayList<>(current);
            if (updated.contains(weather)) {
                if (updated.size() > 1) {
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

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
