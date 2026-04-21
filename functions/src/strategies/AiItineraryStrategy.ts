import { GoogleGenerativeAI } from '@google/generative-ai';
import { ItineraryStrategy }   from './ItineraryStrategy';
import { SearchCriteria, Itinerary, RouteMode, PointOfInterest } from '../models';
import { GooglePlacesService }  from '../services/GooglePlacesService';
import { RoutingService }       from '../services/RoutingService';
import { Env }                  from '../config/AppConfig';
import { Logger }               from '../utils/Logger';

/**
 * Stratégie de génération d'itinéraires propulsée par l'IA (Gemini).
 * 
 * L'IA conçoit le plan (noms des lieux, thématique) et 
 * les services Google valident les coordonnées et tracés réels.
 */
export class AiItineraryStrategy implements ItineraryStrategy {
    private readonly genAI: GoogleGenerativeAI;
    private readonly log: Logger;

    constructor(
        private readonly placesService: GooglePlacesService,
        private readonly routingService: RoutingService
    ) {
        this.log = new Logger('AiItineraryStrategy');
        this.genAI = new GoogleGenerativeAI(Env.GOOGLE_AI_API_KEY);
    }

    async generate(criteria: SearchCriteria): Promise<Itinerary[]> {
        if (!Env.GOOGLE_AI_API_KEY) {
            this.log.warn('GOOGLE_AI_API_KEY absente — impossible d\'utiliser l\'IA.');
            return [];
        }

        try {
            this.log.info(`Génération IA pour ${criteria.destinationCity}`);
            
            // 1. Demander à l'IA de concevoir 3 plans (Economy, Balanced, Comfort)
            const prompt = this.buildPrompt(criteria);
            const model = this.genAI.getGenerativeModel({ 
                model: 'gemini-1.5-flash',
                generationConfig: { responseMimeType: 'application/json' }
            });

            const result = await model.generateContent(prompt);
            const responseText = result.response.text();
            
            // Nettoyage de la réponse (enlève les balises markdown ```json ... ```)
            const cleanJson = responseText.replace(/```json|```/g, '').trim();
            const aiData = JSON.parse(cleanJson);

            // 2. Pour chaque itinéraire suggéré par l'IA, résoudre les vrais POIs
            const variants = await Promise.all(
                (aiData.itineraries || []).map(async (raw: any) => 
                    this.refineItinerary(raw, criteria)
                )
            );

            return variants.filter((v): v is Itinerary => v !== null);

        } catch (err) {
            this.log.error('Erreur lors de la génération IA', err);
            return [];
        }
    }

    private buildPrompt(criteria: SearchCriteria): string {
        return `
            You are a professional travel planner. Create 3 distinct one-day itineraries for ${criteria.destinationCity}.
            
            Constraints:
            - Budget: ${criteria.budgetMin}€ to ${criteria.budgetMax}€
            - Duration: ${criteria.durationMaxHours} hours
            - Interests: ${criteria.interests.join(', ')}
            - Effort: ${criteria.effortLevel}
            - Weather context: ${criteria.weatherPreferences.join(', ')}
            ${criteria.mandatoryPois.length > 0 ? `- MUST INCLUDE: ${criteria.mandatoryPois.join(', ')}` : ''}

            Modes:
            1. ECONOMY: Low cost, using public spaces and free activities.
            2. BALANCED: A mix of popular sights and local gems.
            3. COMFORT: High-quality experiences, less walking, premium locations.

            Return a JSON object with this exact structure:
            {
                "itineraries": [
                    {
                        "mode": "ECONOMY",
                        "title": "Title here",
                        "description": "Short catchy description",
                        "poiNames": ["Place Name 1", "Place Name 2", "Place Name 3"]
                    },
                    ... (repeat for BALANCED and COMFORT)
                ]
            }

            Only return the JSON. Use real, existing places in ${criteria.destinationCity}.
        `;
    }

    private async refineItinerary(aiItin: any, criteria: SearchCriteria): Promise<Itinerary | null> {
        this.log.info(`Raffinage de l'itinéraire : ${aiItin.title}`);

        // Résoudre chaque nom de lieu en PointOfInterest réel
        const poiPromises = aiItin.poiNames.map(async (name: string) => {
            const results = await this.placesService.searchPOIs(criteria.destinationCity, [name]);
            return results.length > 0 ? results[0] : null;
        });

        const pois = (await Promise.all(poiPromises)).filter((p): p is PointOfInterest => p !== null);

        if (pois.length < 2) return null;

        // Calculer les métriques réelles
        const totalCost = pois.reduce((sum, p) => sum + p.baseCost, 0);
        const totalDuration = pois.reduce((sum, p) => sum + p.averageDurationHours, 0);
        const steps = pois.map(p => p.name).join(' → ');
        const polyline = await this.routingService.getRoutePolyline(pois);

        return {
            name: aiItin.title,
            description: aiItin.description,
            cost: totalCost,
            duration: `${Math.round(totalDuration * 10) / 10}h`,
            effort: criteria.effortLevel,
            weather: criteria.weatherPreferences.join(', '),
            steps: steps,
            poiCoordinates: pois.map(p => ({ lat: p.latitude, lng: p.longitude })),
            routeType: aiItin.mode as RouteMode,
            fullSteps: pois,
            imageUrl: pois[0]?.photoUrls?.[0],
            encodedPolyline: polyline
        };
    }
}
