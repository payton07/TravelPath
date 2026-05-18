package com.example.travelpath.data.repository;

import com.example.travelpath.data.dao.ItineraryDao;
import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.data.remote.FirebaseDataSource;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;

/**
 * Source de vérité unique pour les itinéraires.
 *
 * Stratégie cache hybride (Cache-First) :
 * <pre>
 *   1. Room (cache local)  →  valide ?  →  retourner
 *   2. Firebase            →  retourner + écrire Room
 * </pre>
 *
 * La validité du cache est déterminée par {@link Itinerary#CACHE_TTL_MS}
 * et la requête DAO {@link ItineraryDao#getValidCacheByCity}.
 *
 * Tous les appels s'exécutent sur {@link Schedulers#io()} —
 * les ViewModels observent sur AndroidSchedulers.mainThread().
 */
public final class TravelRepository {

    private final ItineraryDao      dao;
    private final FirebaseDataSource remote;

    public TravelRepository(ItineraryDao dao, FirebaseDataSource remote) {
        this.dao    = dao;
        this.remote = remote;
    }

    // =========================================================================
    // Génération d'itinéraires
    // =========================================================================

    /**
     * Retourne des itinéraires depuis le cache Room si valides,
     * sinon appelle le serveur et met à jour le cache.
     */
    public Single<List<Itinerary>> generateJourneys(SearchCriteria criteria) {
        long expiryTime = System.currentTimeMillis() - Itinerary.CACHE_TTL_MS;

        return dao.getValidCacheByCity(criteria.getDestinationCity(), expiryTime)
                .subscribeOn(Schedulers.io())
                .flatMap(cached -> {
                    if (!cached.isEmpty()) {
                        return Single.just(cached);
                    }
                    return fetchAndCache(criteria);
                });
    }

    /**
     * Force un appel réseau (regénération) — ignore le cache local.
     * Utilisé après un délike ou un ajustement de critères.
     */
    public Single<List<Itinerary>> regenerateJourneys(SearchCriteria criteria) {
        return fetchAndCache(criteria);
    }

    private Single<List<Itinerary>> fetchAndCache(SearchCriteria criteria) {
        return remote.generateJourneys(criteria)
                .subscribeOn(Schedulers.io())
                .flatMap(results ->
                    insertAll(results).andThen(Single.just(results))
                );
    }

    // =========================================================================
    // Opérations CRUD locales
    // =========================================================================

    public Completable save(Itinerary itinerary) {
        itinerary.setSaved(true);
        return dao.update(itinerary).subscribeOn(Schedulers.io());
    }

    public Completable update(Itinerary itinerary) {
        return dao.update(itinerary).subscribeOn(Schedulers.io());
    }

    public Completable delete(Itinerary itinerary) {
        return dao.delete(itinerary).subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getAllItineraries() {
        return dao.getAllItineraries().subscribeOn(Schedulers.io());
    }

    public Flowable<List<Itinerary>> getSavedItineraries() {
        return dao.getSavedItineraries().subscribeOn(Schedulers.io());
    }

    public Single<Itinerary> getById(int id) {
        return dao.getById(id).subscribeOn(Schedulers.io());
    }

    // =========================================================================
    // Opérations réseau déléguées
    // =========================================================================

    public Single<String> generatePdf(Itinerary itinerary) {
        return remote.generatePdf(itinerary).subscribeOn(Schedulers.io());
    }

    public Single<Void> rateItinerary(String itineraryId, boolean liked) {
        return remote.rateItinerary(itineraryId, liked).subscribeOn(Schedulers.io());
    }

    public Single<String> saveItineraryCloud(Itinerary itinerary) {
        return remote.saveItineraryCloud(itinerary).subscribeOn(Schedulers.io());
    }

    public Single<String> shareItinerary(Itinerary itinerary) {
        return remote.shareItinerary(itinerary).subscribeOn(Schedulers.io());
    }

    // =========================================================================
    // Maintenance du cache
    // =========================================================================

    /**
     * Supprime les entrées expirées du cache local.
     * À appeler au démarrage de l'app ou en arrière-plan périodiquement.
     */
    public Completable purgeExpiredCache() {
        long expiryTime = System.currentTimeMillis() - Itinerary.CACHE_TTL_MS;
        return dao.purgeExpiredCache(expiryTime).subscribeOn(Schedulers.io());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Completable insertAll(List<Itinerary> itineraries) {
        return Completable.fromAction(() -> {
            for (Itinerary it : itineraries) {
                long roomId = dao.insertAndGetId(it).blockingGet();
                it.setId((int) roomId);
            }
        }).subscribeOn(Schedulers.io());
    }
}
