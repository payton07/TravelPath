import { PointOfInterest, RouteMode } from '../models';

/**
 * Encapsulates the POI scoring logic for itinerary construction.
 *
 * Design patterns:
 *  - Single Responsibility: scoring lives here, not inside ClassicRuleStrategy.
 *  - Open/Closed: new modes or weight profiles can be added without touching callers.
 *  - Strategy-compatible: can be swapped / mocked in tests independently.
 *
 * Scoring model (all sub-scores normalized to [0, 1] before weighting):
 *
 *   score = w_rating    × norm(rating, 1, 5)
 *         + w_proximity × 1/(1 + distanceKm/medianDistanceKm)
 *         + w_slotMatch × (poi fits current time slot ? 1 : 0)
 *         + w_diversity × (category not yet used ? 1 : 0)
 *         + w_modeBonus × modeSpecificBonus(poi, mode)
 *         + w_openNow   × (poi is open ? 1 : 0)
 *         + jitter      (tiny stochastic noise in BALANCED only, for variety)
 *
 * This replaces the previous un-normalized formula that mixed raw rating (1–5),
 * raw cost (0–150), and distance (0–∞) — making weights meaningless across cities.
 */

interface ScoringWeights {
    readonly rating:    number;
    readonly proximity: number;
    readonly slotMatch: number;
    readonly diversity: number;
    readonly modeBonus: number;
    readonly openNow:   number;
}

const MODE_WEIGHTS: Readonly<Record<RouteMode, ScoringWeights>> = {
    [RouteMode.ECONOMY]: {
        rating:    0.30,
        proximity: 0.20,
        slotMatch: 0.10,
        diversity: 0.20,
        modeBonus: -0.30,   // penalizes expensive POIs
        openNow:   0.20,
    },
    [RouteMode.BALANCED]: {
        rating:    0.40,
        proximity: 0.20,
        slotMatch: 0.15,
        diversity: 0.15,
        modeBonus:  0.00,   // neutral
        openNow:   0.10,
    },
    [RouteMode.COMFORT]: {
        rating:    0.35,
        proximity: 0.15,
        slotMatch: 0.10,
        diversity: 0.10,
        modeBonus:  0.30,   // rewards high-comfort POIs
        openNow:   0.10,
    },
};

export class ScoringEngine {

    /**
     * Returns a composite score in roughly [-0.35, 1.0].
     *
     * @param poi               The candidate point of interest.
     * @param mode              Current route mode (ECONOMY / BALANCED / COMFORT).
     * @param distanceKm        Walking distance from the last selected POI (0 for first).
     * @param medianDistanceKm  Median inter-POI distance in the candidate pool —
     *                          used to normalize proximity so that the weight is
     *                          city-agnostic (dense city vs. sprawled suburb).
     * @param usedCategories    Categories already present in the draft itinerary.
     * @param slotMatches       Whether the POI's preferred slot matches the current slot.
     */
    score(
        poi:              PointOfInterest,
        mode:             RouteMode,
        distanceKm:       number,
        medianDistanceKm: number,
        usedCategories:   ReadonlySet<string>,
        slotMatches:      boolean,
    ): number {
        const w = MODE_WEIGHTS[mode];

        const normalizedRating    = (poi.rating - 1) / 4;
        const ref                 = medianDistanceKm > 0 ? medianDistanceKm : 1;
        const normalizedProximity = 1 / (1 + distanceKm / ref);
        const normalizedModeBonus = this.modeBonus(poi, mode);
        const diversityBonus      = usedCategories.has(poi.category) ? 0.0 : 1.0;
        const slotBonus           = slotMatches ? 1.0 : 0.0;
        const openNowBonus        = poi.openingHours?.isOpenNow !== false ? 1.0 : 0.0;

        const baseScore = (
            w.rating    * normalizedRating    +
            w.proximity * normalizedProximity +
            w.slotMatch * slotBonus           +
            w.diversity * diversityBonus      +
            w.modeBonus * normalizedModeBonus +
            w.openNow   * openNowBonus
        );

        // Tiny jitter only for BALANCED — breaks score ties for variety
        // without dominating (±2.5% of the total range).
        const jitter = mode === RouteMode.BALANCED ? (Math.random() - 0.5) * 0.05 : 0;
        return baseScore + jitter;
    }

    /**
     * Computes the median distance between all pairs in the candidate pool.
     * Used once per variant to normalize proximity scores city-independently.
     * O(n²) — acceptable for typical pool sizes of ≤ 60 POIs.
     */
    computeMedianDistance(
        pool: PointOfInterest[],
        calcDist: (a: PointOfInterest, b: PointOfInterest) => number,
    ): number {
        const distances: number[] = [];
        for (let i = 0; i < pool.length; i++) {
            for (let j = i + 1; j < pool.length; j++) {
                distances.push(calcDist(pool[i], pool[j]));
            }
        }
        if (distances.length === 0) return 1;
        distances.sort((a, b) => a - b);
        const mid = Math.floor(distances.length / 2);
        return distances.length % 2 === 0
            ? (distances[mid - 1] + distances[mid]) / 2
            : distances[mid];
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private modeBonus(poi: PointOfInterest, mode: RouteMode): number {
        switch (mode) {
            case RouteMode.ECONOMY:
                return 1 - Math.min(poi.baseCost / 150, 1);   // cheaper → higher bonus
            case RouteMode.COMFORT:
                return Math.min(poi.comfortLevel / 4, 1);     // more comfortable → higher bonus
            case RouteMode.BALANCED:
                return 0.5;                                    // neutral contribution
        }
    }
}
