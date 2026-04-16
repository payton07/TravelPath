import { SearchCriteria } from "../models/SearchCriteria";
import { Itinerary } from "../models/Itinerary";
import { ItineraryStrategy } from "../strategies/ItineraryStrategy";
import { ClassicRuleStrategy } from "../strategies/ClassicRuleStrategy";

/**
 * Service central pilotant la génération d'itinéraires.
 * Gère le choix de la stratégie et le futur cache serveur Firestore.
 */
export class JourneyService {
    private strategy: ItineraryStrategy;

    constructor() {
        // Par défaut on utilise la règle classique. 
        // On pourra ajouter une logique pour choisir l'IA selon l'utilisateur ou la ville.
        this.strategy = new ClassicRuleStrategy();
    }

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        // TODO: Vérifier le cache Firestore ici avant de générer
        
        const itineraries = await this.strategy.generate(criteria);
        
        // TODO: Sauvegarder dans le cache Firestore ici
        
        return itineraries;
    }
}
