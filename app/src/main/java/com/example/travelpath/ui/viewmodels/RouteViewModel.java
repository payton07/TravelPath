package com.example.travelpath.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.logic.JourneyEngine;
import com.example.travelpath.logic.PointOfInterest;
import com.example.travelpath.logic.PlacesService;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;

public class RouteViewModel extends AndroidViewModel {

    private final JourneyEngine engine;
    private final PlacesService placesService;
    private final MutableLiveData<List<Itinerary>> routes = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenerating = new MutableLiveData<>(false);

    public RouteViewModel(@NonNull Application application) {
        super(application);
        this.engine = new JourneyEngine();
        this.placesService = new PlacesService(application);
    }

    public LiveData<List<Itinerary>> getRoutes() { return routes; }
    public LiveData<Boolean> getIsGenerating() { return isGenerating; }

    public void generateRoutes(SearchCriteria criteria) {
        isGenerating.setValue(true);
        
        // Exécution en arrière-plan car l'algo et l'API sont potentiellement lourds
        Single.fromCallable(() -> {
                    // 1. Récupérer les POIs dynamiques pour la ville choisie
                    List<PointOfInterest> dynamicPois = placesService.fetchPOIs(criteria);
                    engine.setPois(dynamicPois);
                    
                    // 2. Générer les 3 variantes de routes
                    return engine.generateRoutes(criteria);
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(generatedRoutes -> {
                    routes.setValue(generatedRoutes);
                    isGenerating.setValue(false);
                }, throwable -> {
                    isGenerating.setValue(false);
                });
    }
}
