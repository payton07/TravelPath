package com.example.travelpath.data.repository;

import android.content.Context;
import com.example.travelpath.data.FirebaseManager;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.database.AppDatabase;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;

public class TravelRepository {

    private final ItineraryDao itineraryDao;

    public TravelRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        itineraryDao = db.itineraryDao();
    }

    /**
     * Logique de génération hybride (Cache local -> Cloud -> Sauvegarde cache).
     */
    public Single<List<Itinerary>> generateJourneys(SearchCriteria criteria) {
        // 1. Vérifier le cache local pour cette ville
        return itineraryDao.getItinerariesByCity(criteria.getDestinationCity())
                .subscribeOn(Schedulers.io())
                .flatMap(localResults -> {
                    if (!localResults.isEmpty()) {
                        // On a des résultats en cache !
                        return Single.just(localResults);
                    } else {
                        // 2. Si vide, appeler le serveur
                        return FirebaseManager.getInstance().generateJourneys(criteria)
                                .observeOn(Schedulers.io()) // RETOUR EN ARRIÈRE-PLAN ICI
                                .flatMap(cloudResults -> {
                                    // 3. Sauvegarder dans Room pour la prochaine fois
                                    return saveToCache(cloudResults).andThen(Single.just(cloudResults));
                                });
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    private Completable saveToCache(List<Itinerary> itineraries) {
        return Completable.fromAction(() -> {
            for (Itinerary it : itineraries) {
                itineraryDao.insert(it).blockingAwait();
            }
        });
    }

    public Completable insert(Itinerary itinerary) {
        return itineraryDao.insert(itinerary).subscribeOn(Schedulers.io());
    }

    public Completable update(Itinerary itinerary) {
        return itineraryDao.update(itinerary).subscribeOn(Schedulers.io());
    }

    public Completable delete(Itinerary itinerary) {
        return itineraryDao.delete(itinerary).subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getAllItineraries() {
        return itineraryDao.getAllItineraries().subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getSavedItineraries() {
        return itineraryDao.getSavedItineraries().subscribeOn(Schedulers.io());
    }

    public Single<Itinerary> getItineraryById(int id) {
        return itineraryDao.getItineraryById(id).subscribeOn(Schedulers.io());
    }
}
