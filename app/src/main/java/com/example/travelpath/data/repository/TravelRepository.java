package com.example.travelpath.data.repository;

import android.content.Context;
import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.database.AppDatabase;
import com.example.travelpath.data.entities.Itinerary;
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

    public Completable insert(Itinerary itinerary) {
        return itineraryDao.insert(itinerary)
                .subscribeOn(Schedulers.io());
    }

    public Completable update(Itinerary itinerary) {
        return itineraryDao.update(itinerary)
                .subscribeOn(Schedulers.io());
    }

    public Completable delete(Itinerary itinerary) {
        return itineraryDao.delete(itinerary)
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getAllItineraries() {
        return itineraryDao.getAllItineraries()
                .subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getSavedItineraries() {
        return itineraryDao.getSavedItineraries()
                .subscribeOn(Schedulers.io());
    }

    public Single<Itinerary> getItineraryById(int id) {
        return itineraryDao.getItineraryById(id)
                .subscribeOn(Schedulers.io());
    }
}
