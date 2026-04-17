import { SearchCriteria, Itinerary }  from '../models';
import { ItineraryStrategy }           from '../strategies/ItineraryStrategy';
import { ClassicRuleStrategy }         from '../strategies/ClassicRuleStrategy';
import { GooglePlacesService }         from './GooglePlacesService';
import { RoutingService }              from './RoutingService';
import { Logger }                      from '../utils/Logger';

/**
 * Orchestre la génération d'itinéraires.
 *
 * Responsabilités :
 *   - Déléguer à la stratégie active
 *   - (TODO) Cache Firestore avant/après génération
 *   - (TODO) Métriques / analytics
 *
 * La stratégie est injectable pour faciliter les tests et l'évolution
 * vers un mode IA sans modifier ce service.
 */
export class JourneyService {

    private readonly log: Logger;

    constructor(
        private readonly strategy: ItineraryStrategy = JourneyService.defaultStrategy(),
        log: Logger = new Logger('JourneyService'),
    ) {
        this.log = log;
    }

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        this.log.info(`Début génération — ville: "${criteria.destinationCity}"`);

        // TODO: Vérifier le cache Firestore ici
        const itineraries = await this.strategy.generate(criteria);
        // TODO: Sauvegarder dans le cache Firestore ici

        this.log.info(`${itineraries.length} itinéraire(s) généré(s).`);
        return itineraries;
    }

    // ─── Fabrique de la stratégie par défaut ──────────────────────────────────

    private static defaultStrategy(): ItineraryStrategy {
        const placesService  = new GooglePlacesService();
        const routingService = new RoutingService();
        return new ClassicRuleStrategy(placesService, routingService);
    }
}