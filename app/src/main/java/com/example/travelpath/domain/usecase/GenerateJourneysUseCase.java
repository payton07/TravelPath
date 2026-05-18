package com.example.travelpath.domain.usecase;

import com.example.travelpath.data.entities.Itinerary;
import com.example.travelpath.data.models.SearchCriteria;
import com.example.travelpath.data.repository.TravelRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Use Case: generate or force-refresh a list of itineraries for given criteria.
 *
 * Design patterns:
 *  - Use Case (Clean Architecture): single entry point per user intent; decouples
 *    ViewModels from repository internals.
 *  - Decorator: transparently wraps repository calls with exponential-backoff retry,
 *    so neither the ViewModel nor the Repository knows about retry logic.
 *
 * Retry policy: up to 3 attempts with 2^attempt-second delays (2s, 4s, 8s).
 * Only retries on network/timeout errors — not on validation or logic errors.
 */
public final class GenerateJourneysUseCase {

    private static final int    MAX_RETRIES  = 3;
    private static final long   BASE_DELAY_S = 2L;

    private final TravelRepository repository;

    public GenerateJourneysUseCase(TravelRepository repository) {
        this.repository = repository;
    }

    /** Returns cached itineraries if valid, or fetches from Firebase. */
    public Single<List<Itinerary>> execute(SearchCriteria criteria) {
        return repository.generateJourneys(criteria)
                .retryWhen(this::buildRetryPolicy);
    }

    /** Bypasses the local cache — forces a new network call. */
    public Single<List<Itinerary>> forceRefresh(SearchCriteria criteria) {
        return repository.regenerateJourneys(criteria)
                .retryWhen(this::buildRetryPolicy);
    }

    // ── Retry policy (exponential backoff) ────────────────────────────────────

    private Flowable<?> buildRetryPolicy(Flowable<Throwable> errors) {
        return errors
                .zipWith(Flowable.range(1, MAX_RETRIES), (err, attempt) -> attempt)
                .flatMap(attempt ->
                    Flowable.timer(BASE_DELAY_S * attempt, TimeUnit.SECONDS));
    }
}
