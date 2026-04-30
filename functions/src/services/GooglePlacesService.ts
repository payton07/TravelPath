import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { PointOfInterest, TimeSlot }            from '../models';
import { Env, PlacesConfig }                    from '../config/AppConfig';
import { Logger }                               from '../utils/Logger';

// ─── Types internes (réponse brute Places API) ────────────────────────────────

interface PlacesApiResult {
    place_id:     string;
    name:         string;
    rating?:      number;
    price_level?: number;
    photos?:      Array<{ photo_reference: string }>;
    opening_hours?: {
        open_now?: boolean;
        weekday_text?: string[];
    };
    geometry: { location: { lat: number; lng: number } };
}

interface PlacesApiResponse {
    status:          string;
    results:         PlacesApiResult[];
    error_message?:  string;
}

// ─── Règles de résolution du créneau horaire ──────────────────────────────────

const TIME_SLOT_RULES: Array<{ keywords: string[]; slot: TimeSlot }> = [
    { keywords: ['food', 'restau', 'dinner', 'cuisine', 'bar', 'café', 'brasserie', 'bistrot'], slot: 'evening'   },
    { keywords: ['musée', 'museum', 'gallery', 'galerie', 'art', 'history', 'monument', 'château', 'église', 'cathédrale', 'abbaye'], slot: 'morning' },
    { keywords: ['sport', 'hike', 'randonnée', 'vélo', 'climb', 'stade', 'arena'],             slot: 'morning'   },
    { keywords: ['parc', 'park', 'jardin', 'garden', 'square', 'place', 'promenade', 'quai', 'plage', 'beach'], slot: 'afternoon' },
    { keywords: ['shopping', 'mall', 'centre commercial', 'boutique', 'magasin'],               slot: 'afternoon' },
];

const DEFAULT_TIME_SLOT: TimeSlot = 'afternoon';

// ─── Service ──────────────────────────────────────────────────────────────────

/**
 * Couche d'accès aux POIs via Google Places Text Search API.
 *
 * Responsabilités :
 *   1. Interroger l'API en parallèle par intérêt
 *   2. Normaliser les résultats bruts en PointOfInterest
 *   3. Fournir un fallback statique si l'API est indisponible
 */
export class GooglePlacesService {

    private readonly apiKey: string;
    private readonly http:   AxiosInstance;
    private readonly log:    Logger;

    constructor(
        log:        Logger        = new Logger('GooglePlacesService'),
        httpClient: AxiosInstance = axios.create({ timeout: 8000 }),
    ) {
        this.apiKey = Env.MAPS_API_KEY;
        this.http   = httpClient;
        this.log    = log;

        if (!this.apiKey) {
            this.log.warn('MAPS_API_KEY absente — le fallback sera utilisé pour toutes les requêtes.');
        }
    }

    // =========================================================================
    // API publique
    // =========================================================================

    async searchPOIs(city: string, interests: string[]): Promise<PointOfInterest[]> {
        if (!this.apiKey) {
            this.log.warn(`Pas de clé API — fallback activé pour "${city}".`);
            return this.buildFallbackPOIs(city);
        }

        this.log.info(`Recherche POIs — ville: "${city}", intérêts: [${interests.join(', ')}]`);

        const settled = await Promise.allSettled(
            interests.map(interest => this.fetchForInterest(city, interest)),
        );

        const pois = this.mergeSettledResults(settled);

        if (pois.length === 0) {
            this.log.warn(`Aucun POI valide pour "${city}" — fallback activé.`);
            return this.buildFallbackPOIs(city);
        }

        this.log.info(`${pois.length} POI(s) retourné(s) pour "${city}".`);
        return pois;
    }

    // =========================================================================
    // Appel API unitaire
    // =========================================================================

    private async fetchForInterest(city: string, interest: string): Promise<PointOfInterest[]> {
        return this.withRetry(() => this.doFetch(city, interest), interest, city);
    }

    private async doFetch(city: string, interest: string): Promise<PointOfInterest[]> {
        const response: AxiosResponse<PlacesApiResponse> = await this.http.get(
            PlacesConfig.BASE_URL,
            { params: { query: `${interest} in ${city}`, key: this.apiKey } },
        );

        const { status, results, error_message } = response.data;

        if (!results?.length) {
            this.log.warn(
                `No results for "${interest}" in ${city}. `
                + `Status: ${status}${error_message ? ` | ${error_message}` : ''}`,
            );
            return [];
        }

        this.log.info(`${results.length} result(s) — "${interest}" in ${city}.`);

        return results
            .slice(0, PlacesConfig.MAX_RESULTS_PER_INTEREST)
            .map(r => this.normalize(r, interest));
    }

    /**
     * Retries fn up to MAX_RETRIES times with exponential backoff + jitter.
     * Specifically handles HTTP 429 (rate limit) and 5xx errors.
     * Other errors are propagated immediately.
     */
    private async withRetry<T>(
        fn:        () => Promise<T>,
        interest:  string,
        city:      string,
        attempt =  1,
        maxRetries = 3,
    ): Promise<T> {
        try {
            return await fn();
        } catch (err: any) {
            const status = err?.response?.status;
            const isRetryable = status === 429 || (status >= 500 && status < 600);

            if (!isRetryable || attempt > maxRetries) {
                this.log.error(`Places API failed for "${interest}" in ${city} (status ${status})`, err);
                throw err;
            }

            // Exponential backoff: 1s, 2s, 4s + random jitter up to 500ms
            const backoffMs = Math.pow(2, attempt - 1) * 1000 + Math.random() * 500;
            this.log.warn(`Rate limited for "${interest}". Retry ${attempt}/${maxRetries} in ${Math.round(backoffMs)}ms.`);

            await new Promise(resolve => setTimeout(resolve, backoffMs));
            return this.withRetry(fn, interest, city, attempt + 1, maxRetries);
        }
    }

    // =========================================================================
    // Normalisation
    // =========================================================================

    private normalize(result: PlacesApiResult, category: string): PointOfInterest {
        const priceLevel = result.price_level ?? 1;

        // Nettoyage du nom pour éviter les \\n dans le JSON final
        const cleanName = result.name.replace(/\n/g, ' ').replace(/\r/g, '').trim();

        // Construction des URLs de photos
        const photoUrls = result.photos
            ?.slice(0, 3)
            .map(p => this.buildPhotoUrl(p.photo_reference)) ?? [];

        const categoryKey = category.toLowerCase();
        const duration = PlacesConfig.DURATION_BY_CATEGORY[categoryKey]
                      ?? PlacesConfig.DEFAULTS.DURATION_HOURS;

        const poi: PointOfInterest = {
            id:                   result.place_id,
            name:                 cleanName,
            category,
            latitude:             result.geometry.location.lat,
            longitude:            result.geometry.location.lng,
            baseCost:             PlacesConfig.COST_BY_PRICE_LEVEL[priceLevel] ?? PlacesConfig.DEFAULTS.COST,
            rating:               result.rating                                ?? PlacesConfig.DEFAULTS.RATING,
            averageDurationHours: duration,
            preferredTimeSlot:    this.resolveTimeSlot(category, result.name),
            weatherCompatibility: this.resolveWeatherCompatibility(category),
            effortScore:          PlacesConfig.DEFAULTS.EFFORT_SCORE,
            comfortLevel:         priceLevel                                    ?? PlacesConfig.DEFAULTS.COMFORT_LEVEL,
            photoUrls,
        };

        if (result.opening_hours) {
            poi.openingHours = {
                isOpenNow: result.opening_hours.open_now ?? true,
                weekdayText: result.opening_hours.weekday_text ?? []
            };
        }

        return poi;
    }

    private buildPhotoUrl(ref: string): string {
        return `https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photo_reference=${ref}&key=${this.apiKey}`;
    }

    private resolveWeatherCompatibility(category: string): string[] {
        const outdoor = ['nature', 'architecture'];
        return outdoor.includes(category.toLowerCase()) ? ['SUN', 'CLOUD'] : ['ANY'];
    }

    private resolveTimeSlot(category: string, name: string): TimeSlot {
        const haystack = `${category} ${name}`.toLowerCase();
        for (const rule of TIME_SLOT_RULES) {
            if (rule.keywords.some(kw => haystack.includes(kw))) return rule.slot;
        }
        return DEFAULT_TIME_SLOT;
    }

    // =========================================================================
    // Fallback statique
    // =========================================================================

    private buildFallbackPOIs(city: string): PointOfInterest[] {
        const poi = (
            id: string, name: string, category: string,
            lat: number, lng: number, cost: number, rating: number,
            duration: number, slot: TimeSlot, weather: string[],
            effort: number, comfort: number,
        ): PointOfInterest => ({
            id, name, category, latitude: lat, longitude: lng,
            baseCost: cost, rating, averageDurationHours: duration,
            preferredTimeSlot: slot, weatherCompatibility: weather,
            effortScore: effort, comfortLevel: comfort,
            photoUrls: []
        });

        return [
            poi('F01', `Musée National de ${city}`,     'Culture',  48.8606, 2.3376, 15, 4.8, 3,   'morning',   ['ANY'],   1, 3),
            poi('F02', `Cathédrale de ${city}`,         'Culture',  48.8530, 2.3499,  0, 4.7, 1,   'morning',   ['ANY'],   1, 2),
            poi('F03', `Bistrot du Centre de ${city}`,  'Food',     48.8531, 2.3861, 20, 4.5, 1.5, 'evening',   ['ANY'],   1, 2),
            poi('F04', `Grand Restaurant de ${city}`,   'Food',     48.8550, 2.3450, 65, 4.9, 2.5, 'evening',   ['ANY'],   1, 5),
            poi('F05', `Parc Royal de ${city}`,         'Nature',   48.8635, 2.3275,  0, 4.4, 2,   'afternoon', ['SUN'],   2, 2),
            poi('F06', `Jardin des Fleurs de ${city}`,  'Nature',   48.8500, 2.3300,  5, 4.2, 1,   'afternoon', ['SUN'],   1, 2),
            poi('F07', `Boutiques de ${city}`,          'Shopping', 48.8670, 2.3000,  0, 4.6, 3,   'afternoon', ['ANY'],   2, 4),
            poi('F08', `Marché Local de ${city}`,       'Food',     48.8560, 2.3520,  8, 4.3, 1,   'morning',   ['ANY'],   1, 1),
            poi('F09', `Centre Sportif de ${city}`,     'Sport',    48.8490, 2.3600, 12, 4.1, 2,   'morning',   ['ANY'],   3, 2),
            poi('F10', `Spa & Wellness de ${city}`,     'Wellness', 48.8610, 2.3410, 45, 4.7, 2.5, 'afternoon', ['ANY'],   1, 5),
        ];
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private mergeSettledResults(
        settled: PromiseSettledResult<PointOfInterest[]>[],
    ): PointOfInterest[] {
        return settled.flatMap(result => {
            if (result.status === 'fulfilled') return result.value;
            this.log.error('Échec appel Places API :', result.reason);
            return [];
        });
    }
}
