import { GooglePlacesService } from '../services/GooglePlacesService';
import { RoutingService }       from '../services/RoutingService';
import { ItineraryStrategy }    from './ItineraryStrategy';
import { Itinerary, PointOfInterest, SearchCriteria, RouteMode, TimeSlot } from '../models';
import { JourneyConfig }        from '../config/AppConfig';
import { Logger }               from '../utils/Logger';

// ─── Types internes ───────────────────────────────────────────────────────────

/** État mutable d'un itinéraire en cours de construction. */
interface ItineraryDraft {
    selectedPOIs:   PointOfInterest[];
    totalCost:      number;
    totalDuration:  number;
    lastPOI:        PointOfInterest | null;
    usedCategories: Set<string>;
}

/** Pondérations du score pour un mode donné. */
interface ScoringConfig {
    costPenaltyPerUnit: number;
    comfortBonus:       number;
}

// ─── Configurations de scoring par mode ──────────────────────────────────────

const SCORING_CONFIGS: Record<RouteMode, ScoringConfig> = {
    [RouteMode.ECONOMY]:  { costPenaltyPerUnit: 0.1,  comfortBonus: 0   },
    [RouteMode.BALANCED]: { costPenaltyPerUnit: 0,    comfortBonus: 0.5 },
    [RouteMode.COMFORT]:  { costPenaltyPerUnit: 0,    comfortBonus: 2.0 },
};

const TIME_SLOTS: TimeSlot[] = ['morning', 'afternoon', 'evening'];

// ─── Stratégie principale ─────────────────────────────────────────────────────

/**
 * Moteur de recommandation déterministe (sans IA).
 * Génère 3 itinéraires (ECONOMY / BALANCED / COMFORT) depuis les mêmes critères.
 */
export class ClassicRuleStrategy implements ItineraryStrategy {

    private readonly log: Logger;

    constructor(
        private readonly placesService:  GooglePlacesService,
        private readonly routingService: RoutingService,
        log: Logger = new Logger('ClassicRuleStrategy'),
    ) {
        this.log = log;
    }

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        this.log.info(`Génération pour "${criteria.destinationCity}"`);

        // 1. Récupération des POIs standards
        const rawPool = await this.placesService.searchPOIs(
            criteria.destinationCity,
            criteria.interests,
        );

        // 2. Récupération SPECIFIQUE des lieux obligatoires pour garantir leur présence
        let mandatoryPool: PointOfInterest[] = [];
        if (criteria.mandatoryPois && criteria.mandatoryPois.length > 0) {
            const settled = await Promise.allSettled(
                criteria.mandatoryPois.map(async m => {
                    // On demande à Google spécifiquement ce lieu précis
                    const res = await this.placesService.searchPOIs(criteria.destinationCity, [m]);
                    // On ne garde que le meilleur résultat pour chaque lieu obligatoire
                    return res.length > 0 ? res[0] : null;
                })
            );
            
            mandatoryPool = settled
                .map(r => r.status === 'fulfilled' ? r.value : null)
                .filter((p): p is PointOfInterest => p !== null);
                
            // Dédoublonnage au cas où plusieurs requêtes renverraient le même lieu
            mandatoryPool = mandatoryPool.filter((poi, index, self) =>
                index === self.findIndex((p) => p.id === poi.id)
            );
        }

        // Fusion des pools sans doublons
        const combinedPool = [...mandatoryPool, ...rawPool].filter((poi, index, self) =>
            index === self.findIndex((p) => p.id === poi.id)
        );

        if (combinedPool.length === 0) {
            this.log.warn('Pool vide — aucun itinéraire généré.');
            return [];
        }

        // Filtre météo global
        const weatherSafePool = this.filterByWeather(combinedPool, criteria.weatherPreferences);

        return this.buildAllVariants(weatherSafePool, criteria, mandatoryPool);
    }

    private filterByWeather(pool: PointOfInterest[], prefs: string[]): PointOfInterest[] {
        if (!prefs || prefs.length === 0) return pool;

        const filtered = pool.filter(p => 
            p.weatherCompatibility.includes('ANY') || 
            p.weatherCompatibility.some(w => prefs.includes(w))
        );

        if (filtered.length < JourneyConfig.MIN_FILTERED_POOL_SIZE) {
            this.log.warn('Filtre météo trop restrictif — utilisation pool complète.');
            return pool;
        }

        return filtered;
    }

    private async buildAllVariants(pool: PointOfInterest[], criteria: SearchCriteria, mandatoryPool: PointOfInterest[]): Promise<Itinerary[]> {
        const variantPromises = Object.values(RouteMode).map(async mode => {
            const filtered   = this.filterByMode(pool, mode);
            const prioritized = this.sortByMode(filtered, mode);
            const itinerary  = this.buildItinerary(prioritized, criteria, mode, mandatoryPool);
            
            if (itinerary && itinerary.fullSteps && itinerary.fullSteps.length >= 2) {
                itinerary.encodedPolyline = await this.routingService.getRoutePolyline(itinerary.fullSteps);
            }
            
            return itinerary;
        });

        const results = await Promise.all(variantPromises);
        return results.filter((it): it is Itinerary => it !== null);
    }

    private filterByMode(pool: PointOfInterest[], mode: RouteMode): PointOfInterest[] {
        const subset = pool.filter(poi => this.matchesModeProfile(poi, mode));
        return subset.length >= JourneyConfig.MIN_FILTERED_POOL_SIZE ? subset : [...pool];
    }

    private matchesModeProfile(poi: PointOfInterest, mode: RouteMode): boolean {
        switch (mode) {
            case RouteMode.ECONOMY:  return poi.comfortLevel <= 1;
            case RouteMode.COMFORT:  return poi.comfortLevel >= 2;
            case RouteMode.BALANCED: return true;
        }
    }

    private sortByMode(pool: PointOfInterest[], mode: RouteMode): PointOfInterest[] {
        return [...pool].sort((a, b) => {
            switch (mode) {
                case RouteMode.ECONOMY:  return a.baseCost - b.baseCost;
                case RouteMode.COMFORT:  return (b.comfortLevel - a.comfortLevel) || (b.rating - a.rating);
                case RouteMode.BALANCED: return b.rating - a.rating;
            }
        });
    }

    private buildItinerary(
        pool:     PointOfInterest[],
        criteria: SearchCriteria,
        mode:     RouteMode,
        mandatoryPool: PointOfInterest[]
    ): Itinerary | null {

        // On démarre directement avec les lieux obligatoires vérifiés par Google !
        const draft = this.initDraftWithMandatory(mandatoryPool);

        for (const slot of TIME_SLOTS) {
            if (draft.selectedPOIs.some(p => p.preferredTimeSlot === slot)) continue;
            this.tryFillSlot(draft, pool, slot, mode, criteria);
        }

        if (draft.selectedPOIs.length === 0) return null;

        draft.selectedPOIs.sort((a, b) => {
            const order = { 'morning': 0, 'afternoon': 1, 'evening': 2 };
            return order[a.preferredTimeSlot] - order[b.preferredTimeSlot];
        });

        return this.toItinerary(draft, criteria, mode);
    }

    private initDraftWithMandatory(mandatoryPool: PointOfInterest[]): ItineraryDraft {
        let cost = 0;
        let duration = 0;
        let last: PointOfInterest | null = null;

        mandatoryPool.forEach(p => {
            cost += p.baseCost;
            duration += p.averageDurationHours;
            if (last) duration += this.travelTimeTo(p, last);
            last = p;
        });

        return {
            // On clone le tableau pour éviter de muter la pool d'origine
            selectedPOIs:   [...mandatoryPool],
            totalCost:      cost,
            totalDuration:  duration,
            lastPOI:        last,
            usedCategories: new Set(mandatoryPool.map(p => p.category)),
        };
    }

    private tryFillSlot(
        draft:    ItineraryDraft,
        pool:     PointOfInterest[],
        slot:     TimeSlot,
        mode:     RouteMode,
        criteria: SearchCriteria,
    ): void {
        const best = this.selectBest(pool, slot, mode, criteria, draft);
        if (!best) return;

        const travelTime = this.travelTimeTo(best, draft.lastPOI);

        draft.selectedPOIs.push(best);
        draft.totalCost     += best.baseCost;
        draft.totalDuration += travelTime + best.averageDurationHours;
        draft.lastPOI        = best;
        draft.usedCategories.add(best.category);
    }

    private selectBest(
        pool:     PointOfInterest[],
        slot:     TimeSlot,
        mode:     RouteMode,
        criteria: SearchCriteria,
        draft:    ItineraryDraft,
    ): PointOfInterest | null {

        let bestPOI:   PointOfInterest | null = null;
        let bestScore: number = -Infinity;

        for (const poi of pool) {
            if (criteria.excludeIds?.includes(poi.id)) continue;
            if (this.isSelected(poi, draft)) continue;

            const travelTime = this.travelTimeTo(poi, draft.lastPOI);
            if (!this.fitsConstraints(poi, travelTime, draft, criteria)) continue;

            let score = this.computeScore(poi, mode, travelTime, draft);

            if (poi.openingHours && poi.openingHours.isOpenNow === false) {
                score -= 20;
            }

            if (poi.preferredTimeSlot === slot) score += JourneyConfig.SLOT_MATCH_BONUS;

            if (score > bestScore) {
                bestScore = score;
                bestPOI   = poi;
            }
        }

        return bestPOI;
    }

    private computeScore(
        poi:        PointOfInterest,
        mode:       RouteMode,
        travelTime: number,
        draft:      ItineraryDraft,
    ): number {
        const { costPenaltyPerUnit, comfortBonus } = SCORING_CONFIGS[mode];

        let score = poi.rating
            - costPenaltyPerUnit * poi.baseCost
            + comfortBonus       * poi.comfortLevel;

        if (draft.lastPOI) {
            const dist = this.routingService.calculateDistance(
                draft.lastPOI.latitude, draft.lastPOI.longitude,
                poi.latitude, poi.longitude,
            );
            score += (1 / (1 + dist)) * JourneyConfig.PROXIMITY_WEIGHT;
        }

        if (draft.usedCategories.has(poi.category)) {
            score *= JourneyConfig.DIVERSITY_SCORE_PENALTY;
        }

        score += Math.random() * 0.5;

        return score;
    }

    private toItinerary(
        draft:    ItineraryDraft,
        criteria: SearchCriteria,
        mode:     RouteMode,
    ): Itinerary {
        const { selectedPOIs, totalCost, totalDuration } = draft;
        const mainImage = selectedPOIs.find(p => p.photoUrls && p.photoUrls.length > 0)?.photoUrls?.[0];

        return {
            name:           this.buildTitle(selectedPOIs, mode, criteria),
            description:    this.buildDescription(mode, criteria),
            cost:           Math.round(totalCost     * 100) / 100,
            duration:       `${Math.round(totalDuration * 10) / 10}h`,
            effort:         this.inferEffort(selectedPOIs),
            weather:        this.inferWeather(selectedPOIs),
            steps:          selectedPOIs.map(p => p.name).join(' → '),
            poiCoordinates: selectedPOIs.map(p => ({ lat: p.latitude, lng: p.longitude })),
            routeType:      mode,
            fullSteps:      selectedPOIs,
            imageUrl:       mainImage
        };
    }

    private buildTitle(pois: PointOfInterest[], mode: RouteMode, criteria: SearchCriteria): string {
        const anchor = pois[0]?.name ?? criteria.destinationCity;
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
        if (pois.length === 0) return 'Easy';
        const avg = pois.reduce((sum, p) => sum + (p.effortScore ?? 1), 0) / pois.length;
        if (avg <= 1.4) return 'Easy';
        if (avg <= 2.4) return 'Moderate';
        return 'High';
    }

    private inferWeather(pois: PointOfInterest[]): string {
        if (pois.length === 0) return 'Any';
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

    private travelTimeTo(dest: PointOfInterest, origin: PointOfInterest | null): number {
        if (!origin) return 0;
        const dist = this.routingService.calculateDistance(
            origin.latitude, origin.longitude,
            dest.latitude,   dest.longitude,
        );
        return this.routingService.estimateTravelTimeHours(dist);
    }

    private fitsConstraints(
        poi:        PointOfInterest,
        travelTime: number,
        draft:      ItineraryDraft,
        criteria:   SearchCriteria,
    ): boolean {
        return (
            draft.totalCost     + poi.baseCost                              <= criteria.budgetMax       &&
            draft.totalDuration + travelTime + poi.averageDurationHours     <= criteria.durationMaxHours
        );
    }

    private isSelected(poi: PointOfInterest, draft: ItineraryDraft): boolean {
        return draft.selectedPOIs.some(p => p.id === poi.id);
    }
}
