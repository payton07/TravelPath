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
import java.util.List;

public class SavedRoutesViewModel extends AndroidViewModel {

    private final TravelRepository repository;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<Itinerary>> savedItineraries = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public SavedRoutesViewModel(@NonNull Application application) {
        super(application);
        repository = ((TravelApplication) application).getRepository();
        loadSavedItineraries();
    }

    private void loadSavedItineraries() {
        isLoading.setValue(true);
        disposables.add(repository.getSavedItineraries()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(itineraries -> {
                    savedItineraries.setValue(itineraries);
                    isLoading.setValue(false);
                }, throwable -> {
                    isLoading.setValue(false);
                }));
    }

    public LiveData<List<Itinerary>> getSavedItineraries() { return savedItineraries; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void deleteItinerary(Itinerary itinerary) {
        disposables.add(repository.delete(itinerary)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
