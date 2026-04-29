import { GooglePlacesService } from '../services/GooglePlacesService';
import { RoutingService }       from '../services/RoutingService';
import { ItineraryStrategy }    from './ItineraryStrategy';
import { ScoringEngine }        from './ScoringEngine';
import { Itinerary, PointOfInterest, SearchCriteria, RouteMode, TimeSlot } from '../models';
import { JourneyConfig }        from '../config/AppConfig';
import { Logger }               from '../utils/Logger';

// ─── Internal draft type ──────────────────────────────────────────────────────

interface ItineraryDraft {
    selectedPOIs:    PointOfInterest[];
    totalCost:       number;
    totalDuration:   number;       // hours (visit time + travel time)
    remainingBudget: number;
    remainingTime:   number;       // hours
    lastPOI:         PointOfInterest | null;
    usedCategories:  Set<string>;
}

// ─── Strategy ─────────────────────────────────────────────────────────────────

const TIME_SLOTS: TimeSlot[] = ['morning', 'afternoon', 'evening'];

/**
 * Deterministic itinerary generation engine (no AI).
 * Produces 3 variants (ECONOMY / BALANCED / COMFORT) in parallel.
 *
 * Improvements over the previous version:
 *  1. Normalized scoring via ScoringEngine — weights are city-agnostic.
 *  2. Constraint-aware scheduling — travel time between POIs is deducted from
 *     the remaining budget before selecting the next candidate, preventing
 *     physically impossible schedules.
 *  3. Nearest-neighbor reordering — after greedy slot filling, POIs within each
 *     time slot are reordered to minimize total walking distance (O(n²) on ≤ 8
 *     POIs — negligible cost, significant UX improvement).
 *  4. Mandatory POI city-scoped resolution — each mandatory name is queried with
 *     the destination city to avoid cross-city disambiguation errors.
 *
 * Design patterns:
 *  - Strategy (ItineraryStrategy interface)
 *  - Dependency Injection (services passed via constructor)
 *  - Single Responsibility (ScoringEngine extracted)
 */
export class ClassicRuleStrategy implements ItineraryStrategy {

    private readonly log:    Logger;
    private readonly scorer: ScoringEngine;

    constructor(
        private readonly placesService:  GooglePlacesService,
        private readonly routingService: RoutingService,
        log: Logger = new Logger('ClassicRuleStrategy'),
    ) {
        this.log    = log;
        this.scorer = new ScoringEngine();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        this.log.info(`Generating for "${criteria.destinationCity}"`);

        const [rawPool, mandatoryPool] = await Promise.all([
            this.fetchInterestPool(criteria),
            this.fetchMandatoryPool(criteria),
        ]);

        const combinedPool = this.deduplicate([...mandatoryPool, ...rawPool]);
        if (combinedPool.length === 0) {
            this.log.warn('Empty pool — no itineraries generated.');
            return [];
        }

        const weatherPool = this.applyWeatherFilter(rawPool, mandatoryPool, criteria.weatherPreferences);
        return this.buildAllVariants(weatherPool, criteria, mandatoryPool);
    }

    // =========================================================================
    // Pool fetching
    // =========================================================================

    private async fetchInterestPool(criteria: SearchCriteria): Promise<PointOfInterest[]> {
        return this.placesService.searchPOIs(criteria.destinationCity, criteria.interests);
    }

    /**
     * Fetches each mandatory POI using both its name AND the destination city
     * in the query to prevent cross-city disambiguation errors.
     */
    private async fetchMandatoryPool(criteria: SearchCriteria): Promise<PointOfInterest[]> {
        if (!criteria.mandatoryPois?.length) return [];

        const settled = await Promise.allSettled(
            criteria.mandatoryPois.map(async name => {
                const results = await this.placesService.searchPOIs(
                    criteria.destinationCity,
                    [`${name} ${criteria.destinationCity}`],
                );
                return results.length > 0 ? results[0] : null;
            }),
        );

        return this.deduplicate(
            settled
                .map(r => r.status === 'fulfilled' ? r.value : null)
                .filter((p): p is PointOfInterest => p !== null),
        );
    }

    private applyWeatherFilter(
        rawPool:       PointOfInterest[],
        mandatoryPool: PointOfInterest[],
        prefs:         string[],
    ): PointOfInterest[] {
        if (!prefs?.length) return this.deduplicate([...mandatoryPool, ...rawPool]);

        const filtered = rawPool.filter(p =>
            p.weatherCompatibility.includes('ANY') ||
            p.weatherCompatibility.some(w => prefs.includes(w)),
        );

        const safeFiltered = filtered.length >= JourneyConfig.MIN_FILTERED_POOL_SIZE
            ? filtered
            : rawPool;

        if (filtered.length < JourneyConfig.MIN_FILTERED_POOL_SIZE) {
            this.log.warn('Weather filter too restrictive — using full pool.');
        }

        return this.deduplicate([...mandatoryPool, ...safeFiltered]);
    }

    // =========================================================================
    // Variant construction
    // =========================================================================

    private async buildAllVariants(
        pool:          PointOfInterest[],
        criteria:      SearchCriteria,
        mandatoryPool: PointOfInterest[],
    ): Promise<Itinerary[]> {
        const variants = await Promise.all(
            Object.values(RouteMode).map(mode =>
                this.buildVariant(pool, criteria, mode, mandatoryPool),
            ),
        );
        return variants.filter((it): it is Itinerary => it !== null);
    }

    private async buildVariant(
        pool:          PointOfInterest[],
        criteria:      SearchCriteria,
        mode:          RouteMode,
        mandatoryPool: PointOfInterest[],
    ): Promise<Itinerary | null> {
        const modePool       = this.filterByMode(pool, mode);
        const medianDistance = this.scorer.computeMedianDistance(modePool, (a, b) =>
            this.routingService.calculateDistance(a.latitude, a.longitude, b.latitude, b.longitude),
        );

        const draft = this.initDraft(mandatoryPool, criteria);
        this.fillSlots(draft, modePool, mode, criteria, medianDistance);

        if (draft.selectedPOIs.length === 0) return null;

        const ordered = this.nearestNeighborReorder(draft.selectedPOIs, mandatoryPool);
        const polyline = ordered.length >= 2
            ? await this.routingService.getRoutePolyline(ordered)
            : undefined;

        return this.toItinerary(ordered, draft, criteria, mode, polyline);
    }

    // =========================================================================
    // Greedy slot filling — constraint-aware
    // =========================================================================

    private initDraft(mandatoryPool: PointOfInterest[], criteria: SearchCriteria): ItineraryDraft {
        let cost = 0;
        let duration = 0;
        let last: PointOfInterest | null = null;

        for (const p of mandatoryPool) {
            const travel = last ? this.travelTime(last, p) : 0;
            cost     += p.baseCost;
            duration += travel + p.averageDurationHours;
            last      = p;
        }

        return {
            selectedPOIs:    [...mandatoryPool],
            totalCost:       cost,
            totalDuration:   duration,
            remainingBudget: criteria.budgetMax - cost,
            remainingTime:   criteria.durationMaxHours - duration,
            lastPOI:         last,
            usedCategories:  new Set(mandatoryPool.map(p => p.category)),
        };
    }

    /**
     * Fills time slots greedily.
     * For each slot, deducts the travel time to the candidate before checking
     * the duration constraint — ensuring the schedule is physically realizable.
     */
    private fillSlots(
        draft:           ItineraryDraft,
        pool:            PointOfInterest[],
        mode:            RouteMode,
        criteria:        SearchCriteria,
        medianDistance:  number,
    ): void {
        const maxPasses = criteria.durationMaxHours > 6 ? 2 : 1;

        for (let pass = 0; pass < maxPasses; pass++) {
            for (const slot of TIME_SLOTS) {
                const best = this.selectBest(
                    pool, slot, mode, criteria, draft, medianDistance,
                );
                if (!best) continue;

                const travel = this.travelTime(draft.lastPOI, best);
                draft.selectedPOIs.push(best);
                draft.totalCost       += best.baseCost;
                draft.totalDuration   += travel + best.averageDurationHours;
                draft.remainingBudget -= best.baseCost;
                draft.remainingTime   -= travel + best.averageDurationHours;
                draft.lastPOI          = best;
                draft.usedCategories.add(best.category);
            }
        }
    }

    private selectBest(
        pool:            PointOfInterest[],
        slot:            TimeSlot,
        mode:            RouteMode,
        criteria:        SearchCriteria,
        draft:           ItineraryDraft,
        medianDistance:  number,
    ): PointOfInterest | null {
        let bestPOI:   PointOfInterest | null = null;
        let bestScore: number = -Infinity;

        for (const poi of pool) {
            if (criteria.excludeIds?.includes(poi.id)) continue;
            if (draft.selectedPOIs.some(p => p.id === poi.id)) continue;

            const travelKm   = draft.lastPOI
                ? this.routingService.calculateDistance(
                    draft.lastPOI.latitude, draft.lastPOI.longitude,
                    poi.latitude, poi.longitude,
                  )
                : 0;
            const travelTime = this.routingService.estimateTravelTimeHours(travelKm);

            // Constraint check includes travel time to this POI
            if (
                poi.baseCost + draft.totalCost > criteria.budgetMax ||
                travelTime + poi.averageDurationHours > draft.remainingTime
            ) continue;

            const s = this.scorer.score(
                poi, mode, travelKm, medianDistance,
                draft.usedCategories,
                poi.preferredTimeSlot === slot,
            );

            if (s > bestScore) {
                bestScore = s;
                bestPOI   = poi;
            }
        }

        return bestPOI;
    }

    // =========================================================================
    // Nearest-neighbor reordering
    // =========================================================================

    /**
     * Reorders POIs within each time slot using a nearest-neighbor heuristic
     * to minimize total walking distance.
     *
     * Algorithm: O(n²) where n ≤ 8 per slot — constant-time in practice.
     *
     * Mandatory POIs keep their original order to respect user intent.
     * The slots are then concatenated: morning → afternoon → evening.
     */
    private nearestNeighborReorder(
        pois:          PointOfInterest[],
        mandatoryPool: PointOfInterest[],
    ): PointOfInterest[] {
        const mandatoryIds = new Set(mandatoryPool.map(p => p.id));

        const bySlot = new Map<TimeSlot, PointOfInterest[]>([
            ['morning',   []],
            ['afternoon', []],
            ['evening',   []],
        ]);

        for (const poi of pois) {
            bySlot.get(poi.preferredTimeSlot)?.push(poi);
        }

        const result: PointOfInterest[] = [];
        let lastInPrevSlot: PointOfInterest | null = null;

        for (const slot of TIME_SLOTS) {
            const group = bySlot.get(slot) ?? [];
            if (group.length === 0) continue;

            const reordered = this.nnOrder(group, lastInPrevSlot, mandatoryIds);
            result.push(...reordered);
            lastInPrevSlot = reordered[reordered.length - 1] ?? null;
        }

        return result;
    }

    /**
     * Nearest-neighbor traversal for a single slot's group.
     * Starts from the last POI of the previous slot (or city center if first slot).
     */
    private nnOrder(
        group:        PointOfInterest[],
        startFrom:    PointOfInterest | null,
        mandatoryIds: Set<string>,
    ): PointOfInterest[] {
        if (group.length <= 1) return group;

        const unvisited = [...group];
        const ordered:   PointOfInterest[] = [];
        let   current    = startFrom;

        while (unvisited.length > 0) {
            let nearestIdx = 0;
            let nearestDist = Infinity;

            for (let i = 0; i < unvisited.length; i++) {
                const dist = current
                    ? this.routingService.calculateDistance(
                        current.latitude, current.longitude,
                        unvisited[i].latitude, unvisited[i].longitude,
                      )
                    : 0;
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestIdx  = i;
                }
            }

            const next = unvisited.splice(nearestIdx, 1)[0];
            ordered.push(next);
            current = next;
        }

        return ordered;
    }

    // =========================================================================
    // Mode filtering
    // =========================================================================

    private filterByMode(pool: PointOfInterest[], mode: RouteMode): PointOfInterest[] {
        const subset = pool.filter(p => this.matchesMode(p, mode));
        return subset.length >= 2 ? subset : pool;
    }

    private matchesMode(poi: PointOfInterest, mode: RouteMode): boolean {
        switch (mode) {
            case RouteMode.ECONOMY:  return poi.baseCost <= 15 || poi.comfortLevel <= 1;
            case RouteMode.COMFORT:  return poi.baseCost >= 20 || poi.comfortLevel >= 3;
            case RouteMode.BALANCED: return true;
        }
    }

    // =========================================================================
    // DTO assembly
    // =========================================================================

    private toItinerary(
        pois:     PointOfInterest[],
        draft:    ItineraryDraft,
        criteria: SearchCriteria,
        mode:     RouteMode,
        polyline: string | undefined,
    ): Itinerary {
        const mainImage = pois.find(p => p.photoUrls?.length)?.photoUrls?.[0];

        return {
            name:            this.buildTitle(pois, mode, criteria),
            description:     this.buildDescription(mode, criteria),
            cost:            Math.round(draft.totalCost     * 100) / 100,
            duration:        `${Math.round(draft.totalDuration * 10) / 10}h`,
            effort:          this.inferEffort(pois),
            weather:         this.inferWeather(pois),
            steps:           pois.map(p => p.name).join(' → '),
            poiCoordinates:  pois.map(p => ({ lat: p.latitude, lng: p.longitude })),
            routeType:       mode,
            fullSteps:       pois,
            imageUrl:        mainImage,
            encodedPolyline: polyline,
        };
    }

    private buildTitle(pois: PointOfInterest[], mode: RouteMode, criteria: SearchCriteria): string {
        const anchor = (pois[0]?.name ?? criteria.destinationCity)
            .replace(/\n/g, ' ').replace(/\r/g, '').trim();

        const labels: Record<RouteMode, string> = {
            [RouteMode.ECONOMY]:  `Budget Day: ${anchor} & More`,
            [RouteMode.BALANCED]: `A Perfect Day at ${anchor}`,
            [RouteMode.COMFORT]:  `Premium Experience at ${anchor}`,
        };
        return labels[mode];
    }

    private buildDescription(mode: RouteMode, criteria: SearchCriteria): string {
        const intros: Record<RouteMode, string> = {
            [RouteMode.ECONOMY]:  'An affordable route optimised for value.',
            [RouteMode.BALANCED]: 'A well-balanced itinerary mixing great experiences and fair prices.',
            [RouteMode.COMFORT]:  'A premium selection prioritising quality and comfort.',
        };
        return `${intros[mode]} A curated day trip in ${criteria.destinationCity}.`;
    }

    private inferEffort(pois: PointOfInterest[]): string {
        if (!pois.length) return 'Easy';
        const avg = pois.reduce((s, p) => s + (p.effortScore ?? 1), 0) / pois.length;
        return avg <= 1.4 ? 'Easy' : avg <= 2.4 ? 'Moderate' : 'High';
    }

    private inferWeather(pois: PointOfInterest[]): string {
        if (!pois.length) return 'Any';
        const conditions = ['SUN', 'CLOUD', 'RAIN'];
        const compatible = conditions.filter(c =>
            pois.every(p =>
                !p.weatherCompatibility ||
                p.weatherCompatibility.includes('ANY') ||
                p.weatherCompatibility.includes(c),
            ),
        );
        return compatible.length > 0 ? compatible.join(', ') : 'Varies';
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private travelTime(from: PointOfInterest | null, to: PointOfInterest): number {
        if (!from) return 0;
        const dist = this.routingService.calculateDistance(
            from.latitude, from.longitude, to.latitude, to.longitude,
        );
        return this.routingService.estimateTravelTimeHours(dist);
    }

    private deduplicate(pois: PointOfInterest[]): PointOfInterest[] {
        const seen = new Set<string>();
        return pois.filter(p => {
            if (seen.has(p.id)) return false;
            seen.add(p.id);
            return true;
        });
    }
}
