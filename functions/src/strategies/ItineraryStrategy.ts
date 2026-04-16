import { Itinerary } from "../models/Itinerary";
import { SearchCriteria } from "../models/SearchCriteria";

/**
 * Interface commune pour toutes les stratégies de génération d'itinéraires.
 * Design Pattern: Strategy
 */
export interface ItineraryStrategy {
    generate(criteria: SearchCriteria): Promise<Itinerary[]>;
}
