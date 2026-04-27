import { SearchCriteria, Itinerary } from '../models';
import { ItineraryStrategy }    from '../strategies/ItineraryStrategy';
import { ClassicRuleStrategy }   from '../strategies/ClassicRuleStrategy';
import { AiItineraryStrategy }    from '../strategies/AiItineraryStrategy';
import { GooglePlacesService }   from './GooglePlacesService';
import { RoutingService }        from './RoutingService';
import { FirestoreService }      from './FirestoreService';
import { WeatherService }        from './WeatherService';
import { Env }                   from '../config/AppConfig';
import { Logger }                from '../utils/Logger';

/**
 * Service central pilotant la génération d'itinéraires.
 * Gère le choix de la stratégie et le cache serveur Firestore.
 */
export class JourneyService {
    private readonly strategy: ItineraryStrategy;
    private readonly firestore: FirestoreService;
    private readonly weather: WeatherService;
    private readonly log: Logger;

    constructor() {
        this.log = new Logger('JourneyService');
        this.firestore = new FirestoreService();
        this.weather = new WeatherService();

        const placesService = new GooglePlacesService();
        const routingService = new RoutingService();
        
        // Choix de la stratégie : IA si clé présente ET non vide, sinon Classique
        const aiKey = Env.GOOGLE_AI_API_KEY;
        if (aiKey && aiKey.trim().length > 0 && aiKey !== 'YOUR_GOOGLE_AI_KEY_HERE') {
            this.log.info('Utilisation de la stratégie IA (Gemini)');
            this.strategy = new AiItineraryStrategy(placesService, routingService);
        } else {
            this.log.info('Utilisation de la stratégie Classique (Règles) - Clé IA absente');
            this.strategy = new ClassicRuleStrategy(placesService, routingService);
        }
    }

    /**
     * Génère des itinéraires en consultant d'abord le cache Firestore.
     */
    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        // 1. Enrichir les critères avec la météo réelle
        if (!criteria.weatherPreferences 
            || criteria.weatherPreferences.length === 0 
            || (criteria.weatherPreferences.length === 1 && criteria.weatherPreferences[0] === 'ANY')) {
            const currentCondition = await this.weather.getCurrentCondition(criteria.destinationCity);
            criteria.weatherPreferences = [currentCondition];
        }

        // 2. Générer une clé de cache unique
        const cacheKey = this.buildCacheKey(criteria);

        // 3. Vérifier le cache
        const cached = await this.firestore.getCachedItineraries(cacheKey);
        if (cached) {
            this.log.info(`Retour des résultats depuis le cache pour ${criteria.destinationCity}`);
            return cached;
        }

        // 4. Si non trouvé, générer via la stratégie par défaut
        let itineraries = await this.strategy.generate(criteria);

        // 5. FALLBACK : Si la stratégie IA n'a rien donné, on tente la stratégie Classique
        if (itineraries.length === 0 && this.strategy instanceof AiItineraryStrategy) {
            this.log.warn('Stratégie IA infructueuse. Tentative de repli sur ClassicRuleStrategy...');
            const placesService = new GooglePlacesService();
            const routingService = new RoutingService();
            const fallbackStrategy = new ClassicRuleStrategy(placesService, routingService);
            itineraries = await fallbackStrategy.generate(criteria);
        }

        // 6. Sauvegarder dans le cache pour 24h
        if (itineraries.length > 0) {
            await this.firestore.setCachedItineraries(cacheKey, itineraries);
        }

        return itineraries;
    }

    /**
     * Construit une clé de cache basée sur la ville et l'intégralité des critères (Problème partiel).
     */
    private buildCacheKey(criteria: SearchCriteria): string {
        const city = criteria.destinationCity.toLowerCase().replace(/[^a-z0-9]/g, '_');
        const sortedInterests = [...criteria.interests].sort().join('_').toLowerCase();
        const sortedWeather = [...criteria.weatherPreferences].sort().join('_').toLowerCase();
        
        return `cache_${city}_${sortedInterests}_${sortedWeather}_b${criteria.budgetMax}_d${criteria.durationMaxHours}_e${criteria.effortLevel}`;
    }
}
