import { Itinerary }      from '../models';
import { SearchCriteria } from '../models';

/**
 * Contrat commun pour toutes les stratégies de génération d'itinéraires.
 * Design Pattern : Strategy
 *
 * Implémentations existantes :
 *   - ClassicRuleStrategy : moteur déterministe sans IA
 *
 * Implémentations futures possibles :
 *   - AiStrategy       : génération via LLM
 *   - HybridStrategy   : combinaison règles + IA
 */
export interface ItineraryStrategy {
    generate(criteria: SearchCriteria): Promise<Itinerary[]>;
}