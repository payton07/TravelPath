/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║         TravelPath — GooglePlacesService  (Firebase / Server)          ║
 * ║  Couche d'accès aux POIs via Google Places Text Search API             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 *
 * Principes appliqués :
 *  - Single Responsibility  : une classe = récupérer et normaliser des POIs
 *  - Dependency Injection   : l'httpClient est injecté (testabilité, stub facile)
 *  - Fail-fast              : validation de la clé API à la construction
 *  - Robustesse             : erreurs isolées par intérêt, fallback explicite
 *  - Pas de logs en prod    : un logger injectable remplace les console.log/warn
 *  - Immutabilité           : toutes les constantes déclarées hors classe
 */

import axios, { AxiosInstance, AxiosResponse } from "axios";
import { PointOfInterest }                      from "../models/PointOfInterest";
import { Logger }                               from "../utils/Logger";

// ─── Constantes ──────────────────────────────────────────────────────────────

const PLACES_API_BASE_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json";

/** Nombre maximum de résultats Places conservés par intérêt. */
const MAX_RESULTS_PER_INTEREST = 8;

/**
 * Coût estimé par "niveau de prix" Google Places (1 → 4).
 * Clé = price_level (0–4), valeur = coût moyen en €.
 */
const COST_BY_PRICE_LEVEL: Record<number, number> = {
    0: 0,
    1: 10,
    2: 25,
    3: 50,
    4: 80,
};

const DEFAULT_COST            = COST_BY_PRICE_LEVEL[1];
const DEFAULT_RATING          = 4.0;
const DEFAULT_DURATION_HOURS  = 2;
const DEFAULT_EFFORT_SCORE    = 1;
const DEFAULT_COMFORT_LEVEL   = 2;

// ─── Types internes ──────────────────────────────────────────────────────────

/** Forme brute d'un résultat Places API — évite les `any` éparpillés. */
interface PlacesApiResult {
    place_id:    string;
    name:        string;
    rating?:     number;
    price_level?: number;
    geometry: {
        location: { lat: number; lng: number };
    };
}

interface PlacesApiResponse {
    status:         string;
    results:        PlacesApiResult[];
    error_message?: string;
}

// ─── Mapping créneau horaire ──────────────────────────────────────────────────

type TimeSlot = 'morning' | 'afternoon' | 'evening';

/**
 * Règles déclaratives pour l'assignation d'un créneau horaire.
 * Évaluées dans l'ordre — la première correspondance gagne.
 */
const TIME_SLOT_RULES: Array<{ keywords: string[]; slot: TimeSlot }> = [
    { keywords: ['food', 'restau', 'dinner', 'cuisine', 'bar'],          slot: 'evening'   },
    { keywords: ['musée', 'museum', 'gallery', 'galerie', 'art', 'history'], slot: 'morning' },
    { keywords: ['sport', 'hike', 'randonnée', 'vélo', 'climb'],        slot: 'morning'   },
];

const DEFAULT_TIME_SLOT: TimeSlot = 'afternoon';

// ─── Classe principale ───────────────────────────────────────────────────────

export class GooglePlacesService {

    private readonly apiKey:    string;
    private readonly http:      AxiosInstance;
    private readonly logger:    Logger;

    /**
     * @param logger     Injectable — par défaut un logger no-op en prod, verbose en dev.
     * @param httpClient Injectable — facilite les tests sans appels réseau réels.
     */
    constructor(
        logger:     Logger      = new Logger('GooglePlacesService'),
        httpClient: AxiosInstance = axios.create(),
    ) {
        this.apiKey = process.env.MAPS_API_KEY ?? '';
        this.http   = httpClient;
        this.logger = logger;

        if (!this.apiKey) {
            // Fail-fast : log immédiat à la construction, pas à chaque appel
            this.logger.warn('MAPS_API_KEY est absente — le fallback sera utilisé pour toutes les requêtes.');
        }
    }

    // =========================================================================
    // API publique
    // =========================================================================

    /**
     * Recherche des POIs pour une ville et une liste d'intérêts.
     * Chaque intérêt est interrogé indépendamment ; les erreurs sont isolées.
     * Retourne le fallback si aucun résultat valide n'est obtenu.
     */
    async searchPOIs(city: string, interests: string[]): Promise<PointOfInterest[]> {
        if (!this.apiKey) {
            this.logger.warn(`Pas de clé API — retour du fallback pour "${city}".`);
            return this.buildFallbackPOIs(city);
        }

        this.logger.info(`Recherche POIs — ville: "${city}", intérêts: [${interests.join(', ')}]`);

        // On parallélise les appels (un par intérêt) pour réduire la latence
        const resultsByInterest = await Promise.allSettled(
            interests.map(interest => this.fetchPOIsForInterest(city, interest)),
        );

        const pois = this.flattenSettledResults(resultsByInterest);

        if (pois.length === 0) {
            this.logger.warn(`Aucun POI valide trouvé pour "${city}" — activation du fallback.`);
            return this.buildFallbackPOIs(city);
        }

        this.logger.info(`${pois.length} POI(s) retourné(s) pour "${city}".`);
        return pois;
    }

    // =========================================================================
    // Étape 1 — Appel API pour un seul intérêt
    // =========================================================================

    /**
     * Interroge Places Text Search pour un couple (ville, intérêt).
     * Lance une exception en cas d'erreur réseau ou de réponse inattendue —
     * l'appelant (Promise.allSettled) l'absorbe et continue les autres intérêts.
     */
    private async fetchPOIsForInterest(
        city:     string,
        interest: string,
    ): Promise<PointOfInterest[]> {
        const response: AxiosResponse<PlacesApiResponse> = await this.http.get(
            PLACES_API_BASE_URL,
            { params: { query: `${interest} in ${city}`, key: this.apiKey } },
        );

        const data = response.data;

        if (!data.results || data.results.length === 0) {
            this.logger.warn(
                `Places API — aucun résultat pour "${interest}" à ${city}. `
                + `Status: ${data.status}${data.error_message ? ` | ${data.error_message}` : ''}`,
            );
            return [];
        }

        this.logger.info(`Places API — ${data.results.length} résultat(s) pour "${interest}" à ${city}.`);

        return data.results
            .slice(0, MAX_RESULTS_PER_INTEREST)
            .map(result => this.normalizePlacesResult(result, interest));
    }

    // =========================================================================
    // Étape 2 — Normalisation d'un résultat Places → PointOfInterest
    // =========================================================================

    /**
     * Transforme un résultat brut Places API en PointOfInterest normalisé.
     * Toutes les valeurs inconnues reçoivent un défaut explicite.
     */
    private normalizePlacesResult(
        result:   PlacesApiResult,
        category: string,
    ): PointOfInterest {
        const priceLevel = result.price_level ?? 1;

        return {
            id:                   result.place_id,
            name:                 result.name,
            category,
            latitude:             result.geometry.location.lat,
            longitude:            result.geometry.location.lng,
            baseCost:             COST_BY_PRICE_LEVEL[priceLevel] ?? DEFAULT_COST,
            rating:               result.rating          ?? DEFAULT_RATING,
            averageDurationHours: DEFAULT_DURATION_HOURS,
            preferredTimeSlot:    this.resolveTimeSlot(category, result.name),
            weatherCompatibility: ['ANY'],
            effortScore:          DEFAULT_EFFORT_SCORE,
            comfortLevel:         priceLevel             ?? DEFAULT_COMFORT_LEVEL,
        };
    }

    // =========================================================================
    // Étape 3 — Résolution du créneau horaire
    // =========================================================================

    /**
     * Détermine le créneau horaire optimal d'un POI à partir de sa catégorie
     * et de son nom, en appliquant les règles déclarées dans TIME_SLOT_RULES.
     */
    private resolveTimeSlot(category: string, name: string): TimeSlot {
        const haystack = `${category} ${name}`.toLowerCase();

        for (const rule of TIME_SLOT_RULES) {
            if (rule.keywords.some(kw => haystack.includes(kw))) {
                return rule.slot;
            }
        }

        return DEFAULT_TIME_SLOT;
    }

    // =========================================================================
    // Étape 4 — Fallback statique
    // =========================================================================

    /**
     * POIs de substitution utilisés quand l'API est indisponible ou sans résultat.
     * Couvre les catégories essentielles pour permettre à l'algorithme de tourner.
     *
     * Note : les coordonnées sont centrées sur Paris à titre d'exemple.
     * En production, utiliser un géocodeur pour centrer sur `city`.
     */
    private buildFallbackPOIs(city: string): PointOfInterest[] {
        return [
            this.fallbackPOI('F01', `Musée National de ${city}`,        'Culture',  48.8606, 2.3376, 15, 4.8, 3, 'morning',   ['ANY'],   1, 3),
            this.fallbackPOI('F02', `Cathédrale de ${city}`,            'Culture',  48.8530, 2.3499,  0, 4.7, 1, 'morning',   ['ANY'],   1, 2),
            this.fallbackPOI('F03', `Bistrot du Centre de ${city}`,     'Food',     48.8531, 2.3861, 20, 4.5, 1.5,'evening',  ['ANY'],   1, 2),
            this.fallbackPOI('F04', `Grand Restaurant de ${city}`,      'Food',     48.8550, 2.3450, 65, 4.9, 2.5,'evening',  ['ANY'],   1, 5),
            this.fallbackPOI('F05', `Parc Royal de ${city}`,            'Nature',   48.8635, 2.3275,  0, 4.4, 2,  'afternoon', ['SUN'],   2, 2),
            this.fallbackPOI('F06', `Jardin des Fleurs de ${city}`,     'Nature',   48.8500, 2.3300,  5, 4.2, 1,  'afternoon', ['SUN'],   1, 2),
            this.fallbackPOI('F07', `Boutiques de ${city}`,             'Shopping', 48.8670, 2.3000,  0, 4.6, 3,  'afternoon', ['ANY'],   2, 4),
            this.fallbackPOI('F08', `Marché Local de ${city}`,          'Food',     48.8560, 2.3520,  8, 4.3, 1,  'morning',   ['ANY'],   1, 1),
            this.fallbackPOI('F09', `Centre Sportif de ${city}`,        'Sport',    48.8490, 2.3600, 12, 4.1, 2,  'morning',   ['ANY'],   3, 2),
            this.fallbackPOI('F10', `Spa & Wellness de ${city}`,        'Wellness', 48.8610, 2.3410, 45, 4.7, 2.5,'afternoon', ['ANY'],   1, 5),
        ];
    }

    /** Constructeur utilitaire pour éviter la répétition dans buildFallbackPOIs. */
    private fallbackPOI(
        id:       string,
        name:     string,
        category: string,
        lat:      number,
        lng:      number,
        cost:     number,
        rating:   number,
        duration: number,
        slot:     TimeSlot,
        weather:  string[],
        effort:   number,
        comfort:  number,
    ): PointOfInterest {
        return {
            id, name, category,
            latitude:             lat,
            longitude:            lng,
            baseCost:             cost,
            rating,
            averageDurationHours: duration,
            preferredTimeSlot:    slot,
            weatherCompatibility: weather,
            effortScore:          effort,
            comfortLevel:         comfort,
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Fusionne les résultats de Promise.allSettled en filtrant les rejections.
     * Chaque rejection est loggée mais n'interrompt pas les autres résultats.
     */
    private flattenSettledResults(
        settled: PromiseSettledResult<PointOfInterest[]>[],
    ): PointOfInterest[] {
        const pois: PointOfInterest[] = [];

        for (const result of settled) {
            if (result.status === 'fulfilled') {
                pois.push(...result.value);
            } else {
                this.logger.error('Échec d\'un appel Places API :', result.reason);
            }
        }

        return pois;
    }
}
