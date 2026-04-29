import { SearchCriteria, Itinerary } from '../models';
import { ItineraryStrategy }    from '../strategies/ItineraryStrategy';
import { ClassicRuleStrategy }  from '../strategies/ClassicRuleStrategy';
import { AiItineraryStrategy }  from '../strategies/AiItineraryStrategy';
import { GooglePlacesService }  from './GooglePlacesService';
import { RoutingService }       from './RoutingService';
import { FirestoreService }     from './FirestoreService';
import { WeatherService }       from './WeatherService';
import { CacheKeyBuilder }      from '../utils/CacheKeyBuilder';
import { Env }                  from '../config/AppConfig';
import { Logger }               from '../utils/Logger';

/**
 * Orchestrates itinerary generation with strategy selection and Firestore caching.
 *
 * Design patterns:
 *  - Strategy: primary and fallback strategies are injected, not constructed
 *    inline — enabling clean testing and runtime swapping without rebuilding
 *    the full service graph (previously the fallback re-instantiated all services).
 *  - Dependency Injection: all collaborators passed via constructor.
 *  - Single Responsibility: cache key computation delegated to CacheKeyBuilder.
 *  - Open/Closed: adding a new strategy requires no changes here — only the
 *    caller (index.ts) changes which strategy is injected.
 */
export class JourneyService {
    private readonly log: Logger;

    constructor(
        private readonly primaryStrategy:  ItineraryStrategy,
        private readonly fallbackStrategy: ItineraryStrategy,
        private readonly firestore:        FirestoreService,
        private readonly weather:          WeatherService,
    ) {
        this.log = new Logger('JourneyService');
    }

    /**
     * Creates a JourneyService with the correct strategy based on available env vars.
     * Factory method isolates the "which strategy?" decision from orchestration logic.
     */
    static create(): JourneyService {
        const log            = new Logger('JourneyService.create');
        const placesService  = new GooglePlacesService();
        const routingService = new RoutingService();
        const classic        = new ClassicRuleStrategy(placesService, routingService);

        const aiKey = Env.GOOGLE_AI_API_KEY;
        const useAi = aiKey && aiKey.trim().length > 0 && aiKey !== 'YOUR_GOOGLE_AI_KEY_HERE';

        if (useAi) {
            log.info('Primary strategy: AI (Gemini). Fallback: ClassicRule.');
            return new JourneyService(
                new AiItineraryStrategy(placesService, routingService),
                classic,
                new FirestoreService(),
                new WeatherService(),
            );
        }

        log.info('Primary strategy: ClassicRule (no AI key).');
        return new JourneyService(
            classic,
            classic,
            new FirestoreService(),
            new WeatherService(),
        );
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns itineraries: Firestore cache → primary strategy → fallback strategy.
     * Enriches criteria with real-time weather when no preference is specified.
     */
    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        const enriched  = await this.enrichWithWeather(criteria);
        const cacheKey  = CacheKeyBuilder.build(enriched);

        const cached = await this.firestore.getCachedItineraries(cacheKey);
        if (cached) {
            this.log.info(`Cache hit for "${enriched.destinationCity}" [${cacheKey}]`);
            return cached;
        }

        let itineraries = await this.primaryStrategy.generate(enriched);

        if (itineraries.length === 0 && this.primaryStrategy !== this.fallbackStrategy) {
            this.log.warn('Primary strategy returned 0 results — trying fallback.');
            itineraries = await this.fallbackStrategy.generate(enriched);
        }

        if (itineraries.length > 0) {
            await this.firestore.setCachedItineraries(cacheKey, itineraries);
        }

        return itineraries;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private async enrichWithWeather(criteria: SearchCriteria): Promise<SearchCriteria> {
        const noPreference =
            !criteria.weatherPreferences?.length ||
            (criteria.weatherPreferences.length === 1 &&
             criteria.weatherPreferences[0] === 'ANY');

        if (!noPreference) return criteria;

        const condition = await this.weather.getCurrentCondition(criteria.destinationCity);
        return { ...criteria, weatherPreferences: [condition] };
    }
}
