import axios from "axios";
import { PointOfInterest } from "../models/PointOfInterest";

export class GooglePlacesService {
    private readonly apiKey: string;

    constructor() {
        this.apiKey = process.env.MAPS_API_KEY || "";
    }

    async searchPOIs(city: string, interests: string[]): Promise<PointOfInterest[]> {
        if (!this.apiKey) {
            console.error("MAPS_API_KEY non configurée dans le .env");
            return [];
        }

        const pois: PointOfInterest[] = [];
        
        // On effectue une recherche pour chaque intérêt de l'utilisateur
        for (const interest of interests) {
            try {
                const response = await axios.get("https://maps.googleapis.com/maps/api/place/textsearch/json", {
                    params: {
                        query: `${interest} in ${city}`,
                        key: this.apiKey
                    }
                });

                const results = response.data.results.slice(0, 5); // 5 meilleurs par catégorie
                
                results.forEach((res: any) => {
                    pois.push({
                        id: res.place_id,
                        name: res.name,
                        category: interest,
                        latitude: res.geometry.location.lat,
                        longitude: res.geometry.location.lng,
                        baseCost: this.estimateCost(res.price_level),
                        rating: res.rating || 4.0,
                        averageDurationHours: this.estimateDuration(interest),
                        preferredTimeSlot: this.assignTimeSlot(interest),
                        weatherCompatibility: ["SUN", "CLOUD"],
                        effortScore: 1,
                        comfortLevel: res.price_level || 2
                    });
                });
            } catch (error) {
                console.error(`Erreur Places API pour ${interest}:`, error);
            }
        }
        return pois;
    }

    private estimateCost(priceLevel: number): number {
        if (!priceLevel) return 15;
        return priceLevel * 20; // Estimation simple
    }

    private estimateDuration(category: string): number {
        if (category.toLowerCase().includes("musée")) return 2.5;
        if (category.toLowerCase().includes("restaurant")) return 1.5;
        return 1.5;
    }

    private assignTimeSlot(category: string): 'morning' | 'afternoon' | 'evening' {
        const cat = category.toLowerCase();
        if (cat.includes("restaurant")) return "evening";
        if (cat.includes("musée")) return "morning";
        return "afternoon";
    }
}
