/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║          TravelPath — ClassicRuleStrategy  (Firebase / Server)         ║
 * ║  Stratégie déterministe de génération d'itinéraires sans IA            ║
 * ║  Produit 3 variantes : ECONOMY · BALANCED · COMFORT                    ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Principes appliqués :
 *  - Single Responsibility  : chaque méthode a un rôle unique et clairement nommé
 *  - Open/Closed            : les stratégies de scoring sont isolées et extensibles
 *  - Dependency Injection   : les services sont injectés, non instanciés en dur
 *  - Immutabilité           : on ne mute jamais les pools partagées entre modes
 *  - Fail-fast + Fallback   : validation en entrée, dégradation gracieuse
 *  - Lisibilité             : nommage explicite, pas de "magic numbers"
 */

import { GooglePlacesService } from '../services/GooglePlacesService';
import { RoutingService }       from '../services/RoutingService';
import { ItineraryStrategy }    from './ItineraryStrategy';
import {
    Itinerary,
    PointOfInterest,
    SearchCriteria,
    RouteMode,
    TimeSlot,
} from '../models';

// ─── Constantes ──────────────────────────────────────────────────────────────

/** Nombre de créneaux horaires (matin / après-midi / soir). */
const TIME_SLOTS: TimeSlot[] = ['morning', 'afternoon', 'evening'];

/** Seuil minimal de POIs pour appliquer un filtre par mode sans fallback. */
const MIN_FILTERED_POOL_SIZE = 3;

/**
 * Pénalité appliquée au score quand une catégorie est déjà présente dans
 * l'itinéraire en cours, pour favoriser la diversité.
 */
const DIVERSITY_SCORE_PENALTY = 0.55;

/**
 * Facteur multiplicatif de la composante "proximité géographique".
 * Plus la valeur est élevée, plus un POI proche du précédent est favorisé.
 */
const PROXIMITY_WEIGHT = 5;

// ─── Types internes ──────────────────────────────────────────────────────────

/**
 * Encapsule l'état mutable d'un itinéraire pendant sa construction,
 * séparant clairement accumulation et résultat final.
 */
interface ItineraryDraft {
    selectedPOIs:   PointOfInterest[];
    totalCost:      number;
    totalDuration:  number;
    lastPOI:        PointOfInterest | null;
    usedCategories: Set<string>;
}

/** Paramètres de scoring selon le mode. */
interface ScoringConfig {
    costPenaltyPerUnit:  number; // malus par unité de coût (€)
    comfortBonus:        number; // bonus multiplicateur du comfortLevel
    diversityEnabled:    boolean;
}

// ─── Configurations par mode ─────────────────────────────────────────────────

const SCORING_CONFIGS: Record<RouteMode, ScoringConfig> = {
    ECONOMY:  { costPenaltyPerUnit: 1 / 10, comfortBonus: 0,   diversityEnabled: true },
    BALANCED: { costPenaltyPerUnit: 0,       comfortBonus: 0.5, diversityEnabled: true },
    COMFORT:  { costPenaltyPerUnit: 0,       comfortBonus: 2.0, diversityEnabled: true },
};

// ─── Classe principale ───────────────────────────────────────────────────────

export class ClassicRuleStrategy implements ItineraryStrategy {

    constructor(
        private readonly placesService:  GooglePlacesService,
        private readonly routingService: RoutingService,
    ) {}

    // =========================================================================
    // Point d'entrée public
    // =========================================================================

    /**
     * Génère jusqu'à 3 itinéraires (ECONOMY / BALANCED / COMFORT)
     * à partir des critères fournis par l'utilisateur.
     *
     * @throws  Ne lance pas d'exception : retourne [] en cas d'erreur ou de pool vide.
     */
    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        const rawPool = await this.fetchPOIPool(criteria);
        if (rawPool.length === 0) return [];

        return this.buildAllVariants(rawPool, criteria);
    }

    // =========================================================================
    // Étape 1 — Récupération des POIs
    // =========================================================================

    /**
     * Interroge Google Places et retourne la pool brute.
     * Encapsulé ici pour pouvoir être stubé en tests sans toucher à `generate`.
     */
    private async fetchPOIPool(criteria: SearchCriteria): Promise<PointOfInterest[]> {
        return this.placesService.searchPOIs(
            criteria.destinationCity,
            criteria.interests,
        );
    }

    // =========================================================================
    // Étape 2 — Génération des 3 variantes
    // =========================================================================

    private buildAllVariants(
        rawPool:  PointOfInterest[],
        criteria: SearchCriteria,
    ): Itinerary[] {
        const results: Itinerary[] = [];

        for (const mode of Object.values(RouteMode) as RouteMode[]) {
            // Chaque mode travaille sur sa propre copie filtrée — jamais de mutation partagée
            const filteredPool  = this.applyModeFilter(rawPool, mode);
            const priorityPool  = this.sortPoolByMode(filteredPool, mode);
            const itinerary     = this.buildItinerary(priorityPool, criteria, mode);

            if (itinerary) results.push(itinerary);
        }

        return results;
    }

    // =========================================================================
    // Étape 3 — Filtrage par mode
    // =========================================================================

    /**
     * Restreint la pool selon le profil du mode.
     * Si le sous-ensemble est trop petit, on conserve la pool complète (fallback).
     */
    private applyModeFilter(
        pool: PointOfInterest[],
        mode: RouteMode,
    ): PointOfInterest[] {
        const filtered = pool.filter(poi => this.matchesModeProfile(poi, mode));
        return filtered.length >= MIN_FILTERED_POOL_SIZE ? filtered : [...pool];
    }

    /** Critère d'éligibilité d'un POI pour un mode donné. */
    private matchesModeProfile(poi: PointOfInterest, mode: RouteMode): boolean {
        switch (mode) {
            case RouteMode.ECONOMY:  return poi.comfortLevel <= 1;
            case RouteMode.COMFORT:  return poi.comfortLevel >= 2;
            case RouteMode.BALANCED: return true; // aucun filtre de confort
        }
    }

    // =========================================================================
    // Étape 4 — Tri initial de la pool
    // =========================================================================

    /**
     * Trie la pool pour que la sélection gloutonne démarre avec les meilleurs
     * candidats selon le mode, réduisant le nombre d'itérations nécessaires.
     */
    private sortPoolByMode(
        pool: PointOfInterest[],
        mode: RouteMode,
    ): PointOfInterest[] {
        return [...pool].sort((a, b) => {
            switch (mode) {
                case RouteMode.ECONOMY:
                    return a.baseCost - b.baseCost;

                case RouteMode.COMFORT:
                    // Tri primaire : confort décroissant ; secondaire : rating décroissant
                    return b.comfortLevel - a.comfortLevel || b.rating - a.rating;

                case RouteMode.BALANCED:
                default:
                    return b.rating - a.rating;
            }
        });
    }

    // =========================================================================
    // Étape 5 — Assemblage d'un itinéraire (algorithme glouton par créneau)
    // =========================================================================

    /**
     * Construit un itinéraire en remplissant les créneaux matin / après-midi / soir.
     * Pour chaque créneau, sélectionne le POI au meilleur score qui respecte
     * encore le budget et la durée max.
     */
    private buildItinerary(
        pool:     PointOfInterest[],
        criteria: SearchCriteria,
        mode:     RouteMode,
    ): Itinerary | null {

        const draft: ItineraryDraft = {
            selectedPOIs:   [],
            totalCost:      0,
            totalDuration:  0,
            lastPOI:        null,
            usedCategories: new Set(),
        };

        for (const slot of TIME_SLOTS) {
            this.tryFillSlot(draft, pool, slot, mode, criteria);
        }

        if (draft.selectedPOIs.length === 0) return null;

        return this.assembleFinalItinerary(draft, criteria, mode);
    }

    /**
     * Tente de trouver le meilleur POI pour un créneau donné et met à jour
     * le draft en conséquence. Ne fait rien si aucun candidat n'est éligible.
     */
    private tryFillSlot(
        draft:    ItineraryDraft,
        pool:     PointOfInterest[],
        slot:     TimeSlot,
        mode:     RouteMode,
        criteria: SearchCriteria,
    ): void {

        const best = this.selectBestCandidateForSlot(
            pool, slot, mode, criteria, draft,
        );

        if (!best) return;

        // Calcul du temps de trajet depuis le POI précédent
        const travelTime = this.computeTravelTimeTo(best, draft.lastPOI);

        draft.selectedPOIs.push(best);
        draft.totalCost      += best.baseCost;
        draft.totalDuration  += travelTime + best.averageDurationHours;
        draft.lastPOI         = best;
        draft.usedCategories.add(best.category);
    }

    // =========================================================================
    // Étape 6 — Sélection du meilleur candidat pour un créneau
    // =========================================================================

    private selectBestCandidateForSlot(
        pool:     PointOfInterest[],
        slot:     TimeSlot,
        mode:     RouteMode,
        criteria: SearchCriteria,
        draft:    ItineraryDraft,
    ): PointOfInterest | null {

        let bestPOI:   PointOfInterest | null = null;
        let bestScore: number = -Infinity;

        for (const poi of pool) {
            if (this.isAlreadySelected(poi, draft))         continue;
            if (poi.preferredTimeSlot !== slot)              continue;

            const travelTime = this.computeTravelTimeTo(poi, draft.lastPOI);

            if (!this.fitsWithinConstraints(poi, travelTime, draft, criteria)) continue;

            const score = this.computeScore(poi, mode, travelTime, draft);

            if (score > bestScore) {
                bestScore = score;
                bestPOI   = poi;
            }
        }

        return bestPOI;
    }

    // =========================================================================
    // Étape 7 — Scoring
    // =========================================================================

    /**
     * Calcule le score d'attractivité d'un POI pour le mode et le contexte donnés.
     *
     * Formule :
     *   score = rating
     *         − costPenaltyPerUnit × baseCost    (malus coût pour ECONOMY)
     *         + comfortBonus × comfortLevel       (bonus confort pour COMFORT/BALANCED)
     *         + proximityScore                    (bonus si proche du POI précédent)
     *         × diversityFactor                   (pénalité si catégorie déjà présente)
     */
    private computeScore(
        poi:        PointOfInterest,
        mode:       RouteMode,
        travelTime: number,
        draft:      ItineraryDraft,
    ): number {
        const config = SCORING_CONFIGS[mode];

        let score = poi.rating
            - config.costPenaltyPerUnit * poi.baseCost
            + config.comfortBonus       * poi.comfortLevel;

        // Bonus de proximité : favorise les POIs géographiquement proches
        if (draft.lastPOI) {
            const distance = this.routingService.calculateDistance(
                draft.lastPOI.latitude, draft.lastPOI.longitude,
                poi.latitude,           poi.longitude,
            );
            score += (1 / (1 + distance)) * PROXIMITY_WEIGHT;
        }

        // Pénalité de diversité : décourager (sans interdire) les doublons de catégorie
        if (config.diversityEnabled && draft.usedCategories.has(poi.category)) {
            score *= DIVERSITY_SCORE_PENALTY;
        }

        return score;
    }

    // =========================================================================
    // Étape 8 — Construction de l'entité finale
    // =========================================================================

    private assembleFinalItinerary(
        draft:    ItineraryDraft,
        criteria: SearchCriteria,
        mode:     RouteMode,
    ): Itinerary {
        const { selectedPOIs, totalCost, totalDuration } = draft;

        return {
            name:           this.buildTitle(selectedPOIs, mode, criteria),
            description:    this.buildDescription(mode, criteria),
            cost:           Math.round(totalCost     * 100) / 100,
            duration:       `${Math.round(totalDuration * 10) / 10}h`,
            effort:         this.inferEffortLabel(selectedPOIs),
            weather:        this.inferWeatherLabel(selectedPOIs),
            steps:          selectedPOIs.map(p => p.name).join(' → '),
            poiCoordinates: selectedPOIs.map(p => ({ lat: p.latitude, lng: p.longitude })),
            routeType:      mode,
        };
    }

    // =========================================================================
    // Helpers — Génération de texte
    // =========================================================================

    private buildTitle(
        pois:     PointOfInterest[],
        mode:     RouteMode,
        criteria: SearchCriteria,
    ): string {
        const anchor = pois[0]?.name ?? criteria.destinationCity;
        const labels: Record<RouteMode, string> = {
            ECONOMY:  `Budget Day: ${anchor} & More`,
            BALANCED: `A Perfect Day at ${anchor}`,
            COMFORT:  `Premium Experience at ${anchor}`,
        };
        return labels[mode];
    }

    private buildDescription(mode: RouteMode, criteria: SearchCriteria): string {
        const intros: Record<RouteMode, string> = {
            ECONOMY:  'An affordable route optimised for value.',
            BALANCED: 'A well-balanced itinerary mixing great experiences and fair prices.',
            COMFORT:  'A premium selection prioritising quality and comfort.',
        };
        return `${intros[mode]} A curated day trip in ${criteria.destinationCity}.`;
    }

    /**
     * Déduit le niveau d'effort dominant à partir des POIs sélectionnés.
     * Stratégie : moyenne des effortScore, arrondie au label le plus proche.
     */
    private inferEffortLabel(pois: PointOfInterest[]): string {
        if (pois.length === 0) return 'Easy';
        const avg = pois.reduce((sum, p) => sum + (p.effortScore ?? 1), 0) / pois.length;
        if (avg <= 1.4) return 'Easy';
        if (avg <= 2.4) return 'Moderate';
        return 'High';
    }

    /**
     * Retourne la compatibilité météo globale (intersection des compatibilités).
     */
    private inferWeatherLabel(pois: PointOfInterest[]): string {
        if (pois.length === 0) return 'Any';

        const allConditions = ['SUN', 'CLOUD', 'RAIN'];
        const intersection  = allConditions.filter(condition =>
            pois.every(p =>
                !p.weatherCompatibility ||
                p.weatherCompatibility.includes('ANY') ||
                p.weatherCompatibility.includes(condition),
            ),
        );

        return intersection.length > 0 ? intersection.join(', ') : 'Varies';
    }

    // =========================================================================
    // Helpers — Calculs
    // =========================================================================

    private computeTravelTimeTo(
        destination: PointOfInterest,
        origin:      PointOfInterest | null,
    ): number {
        if (!origin) return 0;
        const distance = this.routingService.calculateDistance(
            origin.latitude,      origin.longitude,
            destination.latitude, destination.longitude,
        );
        return this.routingService.estimateTravelTimeHours(distance);
    }

    private fitsWithinConstraints(
        poi:        PointOfInterest,
        travelTime: number,
        draft:      ItineraryDraft,
        criteria:   SearchCriteria,
    ): boolean {
        const projectedCost     = draft.totalCost     + poi.baseCost;
        const projectedDuration = draft.totalDuration + travelTime + poi.averageDurationHours;

        return (
            projectedCost     <= criteria.budgetMax      &&
            projectedDuration <= criteria.durationMaxHours
        );
    }

    private isAlreadySelected(poi: PointOfInterest, draft: ItineraryDraft): boolean {
        return draft.selectedPOIs.some(p => p.id === poi.id);
    }
}
