import { Itinerary } from "../models/Itinerary";
import { SearchCriteria } from "../models/SearchCriteria";
import { ItineraryStrategy } from "./ItineraryStrategy";
import { PointOfInterest } from "../models/PointOfInterest";
import { GooglePlacesService } from "../services/GooglePlacesService";
import { RoutingService } from "../services/RoutingService";

export class ClassicRuleStrategy implements ItineraryStrategy {
    private placesService = new GooglePlacesService();
    private routingService = new RoutingService();

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        // 1. Récupérer les vrais POIs via Google
        const pool = await this.placesService.searchPOIs(criteria.destinationCity, criteria.interests);
        
        if (pool.length === 0) return [];

        // 2. Générer les 3 variantes
        const results: Itinerary[] = [];
        const modes: ('ECONOMY' | 'BALANCED' | 'COMFORT')[] = ['ECONOMY', 'BALANCED', 'COMFORT'];

        for (const mode of modes) {
            const itinerary = this.buildItinerary(pool, criteria, mode);
            if (itinerary) results.push(itinerary);
        }

        return results;
    }

    private buildItinerary(pool: PointOfInterest[], criteria: SearchCriteria, mode: 'ECONOMY' | 'BALANCED' | 'COMFORT'): Itinerary | null {
        const selected: PointOfInterest[] = [];
        let totalCost = 0;
        let totalDuration = 0;
        let lastPoi: PointOfInterest | null = null;

        const slots: ('morning' | 'afternoon' | 'evening')[] = ['morning', 'afternoon', 'evening'];

        for (const slot of slots) {
            const best = this.findBestForSlot(pool, slot, mode, criteria, selected, lastPoi, totalCost, totalDuration);
            if (best) {
                if (lastPoi) {
                    const dist = this.routingService.calculateDistance(lastPoi.latitude, lastPoi.longitude, best.latitude, best.longitude);
                    totalDuration += this.routingService.estimateTravelTimeHours(dist);
                }
                selected.push(best);
                totalCost += best.baseCost;
                totalDuration += best.averageDurationHours;
                lastPoi = best;
            }
        }

        if (selected.length === 0) return null;

        return {
            name: `${mode}: ${selected[0].name} Loop`,
            description: `A ${mode.toLowerCase()} day trip in ${criteria.destinationCity}.`,
            cost: Math.round(totalCost * 100) / 100,
            duration: `${Math.round(totalDuration * 10) / 10}h`,
            effort: "Moderate",
            weather: "Optimal in SUN",
            steps: selected.map(p => p.name).join(" → "),
            poiCoordinates: selected.map(p => ({ lat: p.latitude, lng: p.longitude })),
            routeType: mode
        };
    }

    private findBestForSlot(pool: PointOfInterest[], slot: string, mode: string, criteria: SearchCriteria, selected: PointOfInterest[], lastPoi: PointOfInterest | null, currentCost: number, currentDuration: number): PointOfInterest | null {
        let best: PointOfInterest | null = null;
        let bestScore = -Infinity;

        for (const poi of pool) {
            if (selected.find(p => p.id === poi.id)) continue;
            if (poi.preferredTimeSlot !== slot) continue;

            const travelTime = lastPoi ? this.routingService.estimateTravelTimeHours(this.routingService.calculateDistance(lastPoi.latitude, lastPoi.longitude, poi.latitude, poi.longitude)) : 0;

            if (currentCost + poi.baseCost > criteria.budgetMax) continue;
            if (currentDuration + travelTime + poi.averageDurationHours > criteria.durationMaxHours) continue;

            let score = poi.rating;
            if (mode === 'ECONOMY') score += (1 / (1 + poi.baseCost)) * 10;
            if (mode === 'COMFORT') score += poi.comfortLevel * 2;
            
            if (lastPoi) {
                const dist = this.routingService.calculateDistance(lastPoi.latitude, lastPoi.longitude, poi.latitude, poi.longitude);
                score += (1 / (1 + dist)) * 5; // Proximité
            }

            if (score > bestScore) {
                bestScore = score;
                best = poi;
            }
        }
        return best;
    }
}
