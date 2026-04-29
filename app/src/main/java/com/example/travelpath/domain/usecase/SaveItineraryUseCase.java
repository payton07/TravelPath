package com.example.travelpath.domain.usecase;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.repository.TravelRepository;
import io.reactivex.rxjava3.core.Completable;

/**
 * Use Case: toggle the saved state of an itinerary.
 *
 * Centralizes the save/unsave mutation so that RouteViewModel and
 * SavedRoutesViewModel share identical behavior without duplicating logic.
 */
public final class SaveItineraryUseCase {

    private final TravelRepository repository;

    public SaveItineraryUseCase(TravelRepository repository) {
        this.repository = repository;
    }

    /** Flips isSaved and persists the change to Room. */
    public Completable toggleSave(Itinerary itinerary) {
        itinerary.setSaved(!itinerary.isSaved());
        return repository.save(itinerary);
    }

    /** Deletes the itinerary from Room entirely. */
    public Completable delete(Itinerary itinerary) {
        return repository.delete(itinerary);
    }
}
