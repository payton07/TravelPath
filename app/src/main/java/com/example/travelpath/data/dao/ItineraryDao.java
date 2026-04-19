package com.example.travelpath.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.travelpath.data.entities.Itinerary;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

@Dao
public interface ItineraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(Itinerary itinerary);

    @Update
    Completable update(Itinerary itinerary);

    @Delete
    Completable delete(Itinerary itinerary);

    /** Tous les itinéraires, triés du plus récent. Observable — l'UI se met à jour automatiquement. */
    @Query("SELECT * FROM itineraries ORDER BY id DESC")
    Flowable<List<Itinerary>> getAllItineraries();

    /** Uniquement les itinéraires sauvegardés explicitement par l'utilisateur. */
    @Query("SELECT * FROM itineraries WHERE isSaved = 1 ORDER BY id DESC")
    Flowable<List<Itinerary>> getSavedItineraries();

    @Query("SELECT * FROM itineraries WHERE id = :id LIMIT 1")
    Single<Itinerary> getById(int id);

    /**
     * Récupère les itinéraires en cache pour une ville, non expirés.
     * Un itinéraire sauvegardé (isSaved=1) n'est jamais considéré expiré.
     *
     * @param city       Ville de destination
     * @param expiryTime Timestamp minimum acceptable (System.currentTimeMillis() - TTL)
     */
    @Query("SELECT * FROM itineraries " +
           "WHERE destinationCity = :city " +
           "AND (isSaved = 1 OR cachedAt > :expiryTime) " +
           "ORDER BY id DESC")
    Single<List<Itinerary>> getValidCacheByCity(String city, long expiryTime);

    /** Supprime tous les itinéraires de cache expirés (non sauvegardés). */
    @Query("DELETE FROM itineraries WHERE isSaved = 0 AND cachedAt <= :expiryTime")
    Completable purgeExpiredCache(long expiryTime);
}
