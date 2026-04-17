import axios from "axios";
import { PointOfInterest } from "../models/PointOfInterest";

export class GooglePlacesService {
    private readonly apiKey: string;

    constructor() {
        this.apiKey = process.env.MAPS_API_KEY || "";
    }

    async searchPOIs(city: string, interests: string[]): Promise<PointOfInterest[]> {
        console.log(`Recherche pour ${city} (Intérêts: ${interests.join(",")})`);
        
        if (!this.apiKey) return this.getFallbackPOIs(city);

        const pois: PointOfInterest[] = [];
        for (const interest of interests) {
            try {
                const response = await axios.get("https://maps.googleapis.com/maps/api/place/textsearch/json", {
                    params: { query: `${interest} in ${city}`, key: this.apiKey }
                });

                if (response.data.results) {
                    response.data.results.slice(0, 8).forEach((res: any) => {
                        pois.push({
                            id: res.place_id,
                            name: res.name,
                            category: interest,
                            latitude: res.geometry.location.lat,
                            longitude: res.geometry.location.lng,
                            baseCost: (res.price_level || 1) * 15,
                            rating: res.rating || 4.0,
                            averageDurationHours: 2,
                            preferredTimeSlot: this.assignTimeSlot(interest, res.name),
                            weatherCompatibility: ["ANY"],
                            effortScore: 1,
                            comfortLevel: res.price_level || 2
                        });
                    });
                }
            } catch (e) { console.error(e); }
        }
        return pois.length > 0 ? pois : this.getFallbackPOIs(city);
    }

    private getFallbackPOIs(city: string): PointOfInterest[] {
        return [
            { id: "F1", name: `Musée National de ${city}`, category: "Culture", latitude: 48.8606, longitude: 2.3376, baseCost: 15, rating: 4.8, averageDurationHours: 3, preferredTimeSlot: "morning", weatherCompatibility: ["ANY"], effortScore: 1, comfortLevel: 3 },
            { id: "F2", name: `Cathédrale de ${city}`, category: "Culture", latitude: 48.8530, longitude: 2.3499, baseCost: 0, rating: 4.7, averageDurationHours: 1, preferredTimeSlot: "morning", weatherCompatibility: ["ANY"], effortScore: 1, comfortLevel: 2 },
            { id: "F3", name: "Bistrot du Centre", category: "Food", latitude: 48.8531, longitude: 2.3861, baseCost: 20, rating: 4.5, averageDurationHours: 1.5, preferredTimeSlot: "evening", weatherCompatibility: ["ANY"], effortScore: 1, comfortLevel: 3 },
            { id: "F4", name: "Grand Restaurant Gastronomique", category: "Food", latitude: 48.8550, longitude: 2.3450, baseCost: 65, rating: 4.9, averageDurationHours: 2.5, preferredTimeSlot: "evening", weatherCompatibility: ["ANY"], effortScore: 1, comfortLevel: 5 },
            { id: "F5", name: "Parc Royal", category: "Nature", latitude: 48.8635, longitude: 2.3275, baseCost: 0, rating: 4.4, averageDurationHours: 2, preferredTimeSlot: "afternoon", weatherCompatibility: ["SUN"], effortScore: 2, comfortLevel: 2 },
            { id: "F6", name: "Jardin des Fleurs", category: "Nature", latitude: 48.8500, longitude: 2.3300, baseCost: 5, rating: 4.2, averageDurationHours: 1, preferredTimeSlot: "afternoon", weatherCompatibility: ["SUN"], effortScore: 1, comfortLevel: 3 },
            { id: "F7", name: "Boutiques de Luxe", category: "Shopping", latitude: 48.8670, longitude: 2.3000, baseCost: 0, rating: 4.6, averageDurationHours: 3, preferredTimeSlot: "afternoon", weatherCompatibility: ["ANY"], effortScore: 2, comfortLevel: 5 }
        ];
    }

    private assignTimeSlot(cat: string, name: string): 'morning' | 'afternoon' | 'evening' {
        const c = cat.toLowerCase();
        if (c.includes("food") || c.includes("restau")) return "evening";
        if (c.includes("musée") || c.includes("art")) return "morning";
        return "afternoon";
    }
}
