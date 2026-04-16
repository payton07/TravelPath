package com.example.travelpath.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import com.example.travelpath.data.entities.Itinerary;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

@Dao
public interface ItineraryDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    Completable insert(Itinerary itinerary);

    @Update
    Completable update(Itinerary itinerary);

    @Delete
    Completable delete(Itinerary itinerary);

    @Query("SELECT * FROM itineraries ORDER BY id DESC")
    Flowable<List<Itinerary>> getAllItineraries();

    @Query("SELECT * FROM itineraries WHERE isSaved = 1 ORDER BY id DESC")
    Flowable<List<Itinerary>> getSavedItineraries();

    @Query("SELECT * FROM itineraries WHERE id = :id")
    Single<Itinerary> getItineraryById(int id);

    @Query("SELECT * FROM itineraries WHERE destinationCity = :city ORDER BY id DESC")
    Single<List<Itinerary>> getItinerariesByCity(String city);
}
