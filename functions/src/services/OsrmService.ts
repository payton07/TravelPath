import axios, { AxiosInstance } from 'axios';
import { Logger } from '../utils/Logger';

const OSRM_BASE_URL = 'https://router.project-osrm.org/route/v1/walking';

/**
 * Service de routage Open Source (OSRM).
 * Utilisé pour calculer distances et temps de trajet sans frais d'API Google.
 */
export class OsrmService {
    private readonly http: AxiosInstance;
    private readonly log: Logger;

    constructor(
        log: Logger = new Logger('OsrmService'),
        httpClient: AxiosInstance = axios.create({ timeout: 5000 })
    ) {
        this.log = log;
        this.http = httpClient;
    }

    /**
     * Calcule la distance et la durée entre deux points via OSRM.
     */
    async getRoute(lat1: number, lon1: number, lat2: number, lon2: number): Promise<{ distanceKm: number, durationHours: number }> {
        try {
            const url = `${OSRM_BASE_URL}/${lon1},${lat1};${lon2},${lat2}?overview=false`;
            const response = await this.http.get(url);

            if (response.data.code === 'Ok' && response.data.routes?.length > 0) {
                const route = response.data.routes[0];
                return {
                    distanceKm: route.distance / 1000,
                    durationHours: route.duration / 3600
                };
            }
            throw new Error('OSRM : Aucun itinéraire trouvé');
        } catch (err) {
            this.log.error('Erreur OSRM', err);
            // Fallback : calcul Haversine si OSRM échoue
            const dist = this.haversine(lat1, lon1, lat2, lon2);
            return {
                distanceKm: dist,
                durationHours: dist / 4.5 // Estimation 4.5 km/h
            };
        }
    }

    private haversine(lat1: number, lon1: number, lat2: number, lon2: number): number {
        const R = 6371;
        const toRad = (d: number) => (d * Math.PI) / 180;
        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                  Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                  Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
