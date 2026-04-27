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
            
            const prompt = this.buildPrompt(criteria);
            const model = this.genAI.getGenerativeModel({ 
                model: 'gemini-1.5-flash',
                generationConfig: { responseMimeType: 'application/json' }
            });

            const result = await model.generateContent(prompt);
            const responseText = result.response.text();
            this.log.info(`Réponse brute de l'IA reçue (length: ${responseText.length})`);
            
            // Nettoyage de la réponse
            const cleanJson = responseText.replace(/```json|```/g, '').trim();
            const aiData = JSON.parse(cleanJson);

            if (!aiData.itineraries || !Array.isArray(aiData.itineraries)) {
                this.log.error('Format JSON IA invalide (itineraries manquant)');
                return [];
            }

            // 2. Pour chaque itinéraire suggéré par l'IA, résoudre les vrais POIs
            const variants = await Promise.all(
                aiData.itineraries.map(async (raw: any) => 
                    this.refineItinerary(raw, criteria)
                )
            );

            const finalResults = variants.filter((v): v is Itinerary => v !== null);
            this.log.info(`${finalResults.length} itinéraires IA validés.`);
            
            return finalResults;

        } catch (err) {
            this.log.error('Erreur lors de la génération IA', err);
            return [];
        }
    }

    private buildPrompt(criteria: SearchCriteria): string {
        const poiCount = criteria.durationMaxHours > 7 ? '5 to 6' : '3 to 4';

        return `
            You are a professional travel planner expert in ${criteria.destinationCity}. 
            Create 3 distinct one-day itineraries for this city.
            
            Target City: ${criteria.destinationCity}
            Constraints:
            - Budget: ${criteria.budgetMin}€ to ${criteria.budgetMax}€
            - Duration: ${criteria.durationMaxHours} hours
            - Interests: ${criteria.interests.join(', ')}
            - Effort: ${criteria.effortLevel}
            - Weather context: ${criteria.weatherPreferences.join(', ')}
            ${criteria.mandatoryPois.length > 0 ? `- MUST INCLUDE these exact places: ${criteria.mandatoryPois.join(', ')}` : ''}

            Return a valid JSON object with exactly 3 itineraries. 
            
            IMPORTANT DIVERSITY RULES:
            - Each itinerary MUST be unique.
            - You MUST NOT repeat the same POI in different itineraries (except for Mandatory stops).
            - Each itinerary MUST have ${poiCount} different POIs based on the ${criteria.durationMaxHours}h duration.
            - Total of different POIs across the whole response should be around ${criteria.durationMaxHours > 7 ? '15' : '9'}.

            JSON Structure:
            {
                "itineraries": [
                    {
                        "mode": "ECONOMY",
                        "title": "Short title",
                        "description": "Catchy description",
                        "poiNames": ["Famous Place 1", "Famous Place 2", "Famous Place 3"]
                    }
                ]
            }

            Important: Use ONLY real and famous places that can be easily found on Google Maps. 
            Do not include any text outside the JSON.
            City: ${criteria.destinationCity}
        `;
    }

    private async refineItinerary(aiItin: any, criteria: SearchCriteria): Promise<Itinerary | null> {
        this.log.info(`Raffinage de l'itinéraire : "${aiItin.title}" avec POIs: [${aiItin.poiNames?.join(', ')}]`);

        if (!aiItin.poiNames || !Array.isArray(aiItin.poiNames) || aiItin.poiNames.length === 0) {
            this.log.warn(`L'itinéraire "${aiItin.title}" n'a pas de POIs valides.`);
            return null;
        }

        // Résoudre chaque nom de lieu en PointOfInterest réel
        const poiPromises = aiItin.poiNames.map(async (name: string) => {
            const results = await this.placesService.searchPOIs(criteria.destinationCity, [name]);
            if (results.length > 0) {
                this.log.info(`  ✅ Trouvé : "${name}" -> "${results[0].name}"`);
                return results[0];
            }
            this.log.warn(`  ❌ Non trouvé : "${name}" à ${criteria.destinationCity}`);
            return null;
        });

        const pois = (await Promise.all(poiPromises)).filter((p): p is PointOfInterest => p !== null);

        if (pois.length < 2) {
            this.log.warn(`Itinéraire rejeté : trop peu de POIs valides (${pois.length}/2 requis).`);
            return null;
        }

        // Calculer les métriques réelles
        const totalCost = pois.reduce((sum, p) => sum + p.baseCost, 0);
        const totalDuration = pois.reduce((sum, p) => sum + p.averageDurationHours, 0);
        const steps = pois.map(p => p.name).join(' → ');
        const polyline = await this.routingService.getRoutePolyline(pois);

        return {
            name: aiItin.title.replace(/\n/g, ' ').replace(/\r/g, '').trim(),
            description: (aiItin.description || '').replace(/\n/g, ' ').replace(/\r/g, '').trim(),
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
