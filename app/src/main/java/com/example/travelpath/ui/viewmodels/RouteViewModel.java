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
import java.util.List;

public class RouteViewModel extends AndroidViewModel {

    private final TravelRepository repository;
    private final CompositeDisposable disposables = new CompositeDisposable();
    
    private final MutableLiveData<List<Itinerary>> routes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public RouteViewModel(@NonNull Application application) {
        super(application);
        this.repository = TravelApplication.getRepository();
    }

    public LiveData<List<Itinerary>> getRoutes() { return routes; }
    public LiveData<Boolean> getIsGenerating() { return isGenerating; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public void generateRoutes(SearchCriteria criteria) {
        isGenerating.setValue(true);
        errorMessage.setValue(null);
        
        disposables.add(repository.generateJourneys(criteria)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(generatedRoutes -> {
                    routes.setValue(generatedRoutes);
                    isGenerating.setValue(false);
                }, throwable -> {
                    errorMessage.setValue(throwable.getMessage());
                    isGenerating.setValue(false);
                }));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
