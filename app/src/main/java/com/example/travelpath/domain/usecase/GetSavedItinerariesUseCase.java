package com.example.travelpath.domain.usecase;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.repository.TravelRepository;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;

/**
 * Use Case: observe the live stream of saved itineraries from Room.
 *
 * Returns a Flowable that emits a new list on every database mutation
 * (insert / update / delete) — no manual refresh required.
 */
public final class GetSavedItinerariesUseCase {

    private final TravelRepository repository;

    public GetSavedItinerariesUseCase(TravelRepository repository) {
        this.repository = repository;
    }

    public Flowable<List<Itinerary>> execute() {
        return repository.getSavedItineraries();
    }
}
