import { SearchCriteria } from "../models/SearchCriteria";
import { Itinerary } from "../models/Itinerary";
import { ItineraryStrategy } from "../strategies/ItineraryStrategy";
import { ClassicRuleStrategy } from "../strategies/ClassicRuleStrategy";

import { GooglePlacesService } from "./GooglePlacesService";
import { RoutingService } from "./RoutingService";
import { Logger } from "../utils/Logger";

/**
 * Service central pilotant la génération d'itinéraires.
 * Gère le choix de la stratégie et le futur cache serveur Firestore.
 */
export class JourneyService {
    private strategy: ItineraryStrategy;

    constructor() {
        // Injection des services nécessaires à la stratégie
        const placesService = new GooglePlacesService(new Logger('GooglePlacesService'));
        const routingService = new RoutingService();
        
        this.strategy = new ClassicRuleStrategy(placesService, routingService);
    }

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        // TODO: Vérifier le cache Firestore ici avant de générer
        
        const itineraries = await this.strategy.generate(criteria);
        
        // TODO: Sauvegarder dans le cache Firestore ici
        
        return itineraries;
    }
}
